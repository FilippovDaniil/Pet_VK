package com.socialnetwork.security;

import com.socialnetwork.service.BlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Фильтр аутентификации на основе JWT-токенов.
 *
 * <p><b>Место в архитектуре Spring Security:</b><br>
 * Spring Security представляет собой цепочку фильтров (Filter Chain). Каждый HTTP-запрос
 * проходит через эту цепочку последовательно. Наш фильтр встраивается ДО стандартного
 * {@link UsernamePasswordAuthenticationFilter}, чтобы перехватить JWT ещё до того,
 * как Spring попытается найти форму логина или Basic-аутентификацию.
 *
 * <p><b>Почему {@code OncePerRequestFilter}?</b><br>
 * В Servlet-контейнерах (Tomcat, Jetty) один HTTP-запрос может пройти через фильтры
 * несколько раз (например, при forward/include). {@link OncePerRequestFilter} гарантирует,
 * что {@code doFilterInternal} вызовется ровно один раз на запрос, что критично для
 * корректной установки аутентификации в SecurityContext.
 *
 * <p><b>Принцип работы:</b>
 * <ol>
 *   <li>Извлечь JWT из заголовка {@code Authorization: Bearer <token>}</li>
 *   <li>Проверить подпись и срок действия токена</li>
 *   <li>Убедиться, что токен не в чёрном списке (после logout)</li>
 *   <li>Загрузить UserDetails из БД и установить аутентификацию в SecurityContext</li>
 *   <li>Передать запрос дальше по цепочке</li>
 * </ol>
 *
 * @see OncePerRequestFilter
 * @see SecurityContextHolder
 */
@Component
// @Component — регистрирует этот фильтр как Spring Bean.
// SecurityConfig затем явно добавляет его в цепочку через addFilterBefore().
// Важно: без явного добавления в SecurityConfig фильтр будет зарегистрирован
// как обычный Servlet-фильтр, что может привести к двойному выполнению.
@RequiredArgsConstructor
// @RequiredArgsConstructor (Lombok) генерирует конструктор со всеми final-полями.
// Это стандартный способ конструкторной инъекции зависимостей в Spring:
// Spring автоматически находит единственный конструктор и инжектирует нужные бины.
@Slf4j
// @Slf4j (Lombok) генерирует поле log типа Logger — для логирования событий.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Провайдер JWT: парсит, валидирует токены и извлекает из них данные.
     */
    private final JwtTokenProvider tokenProvider;

    /**
     * Сервис для загрузки деталей пользователя из БД по email.
     * Spring Security требует UserDetails для создания объекта аутентификации.
     */
    private final CustomUserDetailsService userDetailsService;

    /**
     * Сервис чёрного списка токенов (реализован через Redis).
     * После операции logout токен добавляется в чёрный список,
     * чтобы его нельзя было использовать повторно до истечения TTL.
     */
    private final BlacklistService blacklistService;

    /**
     * Основная логика фильтра — выполняется один раз на каждый HTTP-запрос.
     *
     * <p>Метод НЕ выбрасывает исключение при невалидном токене — он просто
     * не устанавливает аутентификацию. Последующие фильтры в цепочке
     * (или {@code ExceptionTranslationFilter}) решат, вернуть ли 401 Unauthorized.
     *
     * @param request     входящий HTTP-запрос
     * @param response    исходящий HTTP-ответ
     * @param filterChain цепочка фильтров — следующий фильтр вызывается через doFilter()
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Шаг 1: Пытаемся извлечь токен из заголовка Authorization.
        // Если заголовка нет — token будет null и мы просто пропустим блок аутентификации.
        String token = extractToken(request);

        // Шаг 2: Тройная проверка перед установкой аутентификации.
        // Все три условия должны быть выполнены одновременно — используем &&
        // для короткого замыкания (если token == null, остальные проверки не выполняются).
        if (token != null
                && tokenProvider.validateToken(token)      // подпись корректна и токен не просрочен
                && !blacklistService.isBlacklisted(token)) { // токен не был инвалидирован при logout

            // Шаг 3: Извлекаем email из payload токена.
            // Email хранится в стандартном claim "sub" (subject).
            String email = tokenProvider.getEmailFromToken(token);

            // Шаг 4: Загружаем актуальные данные пользователя из БД.
            // Это нужно, чтобы проверить, не заблокирован ли аккаунт (accountLocked).
            // Альтернатива — хранить все данные в токене и не ходить в БД,
            // но тогда изменения (например, бан) не применятся сразу.
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Шаг 5: Создаём объект аутентификации для Spring Security.
            // UsernamePasswordAuthenticationToken(principal, credentials, authorities):
            //   - principal — UserDetails с данными пользователя
            //   - credentials — null, т.к. пароль не нужен при JWT-аутентификации
            //   - authorities — список ролей/прав из UserDetails
            // Передача authorities в конструктор помечает токен как "authenticated = true".
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // Шаг 6: Добавляем дополнительные детали запроса (IP, session id).
            // WebAuthenticationDetailsSource создаёт объект WebAuthenticationDetails,
            // который может использоваться для аудита и логирования.
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Шаг 7: Регистрируем аутентификацию в SecurityContext текущего потока.
            // SecurityContextHolder хранит контекст в ThreadLocal — он уникален для каждого потока.
            // Именно отсюда Spring Security читает информацию о текущем пользователе
            // при проверке @PreAuthorize, SecurityContextHolder.getContext().getAuthentication() и т.д.
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // Шаг 8: ВСЕГДА передаём запрос дальше по цепочке фильтров.
        // Даже если токен невалиден — запрос продолжит движение.
        // SecurityConfig определит, разрешён ли доступ к конкретному URL без аутентификации.
        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает строку токена из HTTP-заголовка {@code Authorization}.
     *
     * <p>Стандарт Bearer-аутентификации (RFC 6750) определяет формат заголовка:
     * {@code Authorization: Bearer <token>}
     * Префикс "Bearer " (с пробелом) занимает 7 символов, поэтому {@code substring(7)}.
     *
     * @param request HTTP-запрос
     * @return строка JWT без префикса "Bearer ", или {@code null} если заголовок отсутствует/некорректен
     */
    private String extractToken(HttpServletRequest request) {
        // Читаем заголовок Authorization из запроса.
        String header = request.getHeader("Authorization");

        // StringUtils.hasText() проверяет: строка не null, не пустая и не состоит только из пробелов.
        // Это надёжнее, чем просто header != null && !header.isEmpty().
        // Проверяем префикс "Bearer " — стандартный индикатор Bearer-токена.
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            // Отрезаем префикс "Bearer " (7 символов) и возвращаем чистый JWT.
            return header.substring(7);
        }

        // Заголовок отсутствует или имеет неожиданный формат — возвращаем null.
        // Фильтр продолжит работу без установки аутентификации.
        return null;
    }
}
