package com.socialnetwork.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.dto.response.AuthResponse;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.repository.UserRepository;
import com.socialnetwork.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Обработчик успешной OAuth2-аутентификации (вход через Google и другие провайдеры).
 *
 * <p><b>Что такое OAuth2 Authorization Code Flow?</b><br>
 * OAuth2 — протокол делегированной авторизации. При входе "через Google" происходит:
 * <ol>
 *   <li>Наш сервер перенаправляет пользователя на страницу Google</li>
 *   <li>Пользователь вводит логин/пароль на стороне Google (мы их никогда не видим)</li>
 *   <li>Google перенаправляет обратно с временным кодом авторизации</li>
 *   <li>Spring Security обменивает код на access_token Google</li>
 *   <li>Spring запрашивает у Google данные профиля (email, имя) через этот token</li>
 *   <li>Spring вызывает наш {@code onAuthenticationSuccess} с данными пользователя</li>
 * </ol>
 *
 * <p><b>Наша задача в этом обработчике:</b><br>
 * Конвертировать успешную OAuth2-аутентификацию в наши собственные JWT-токены,
 * реализовав паттерн "federation gateway": мы используем Google только для проверки личности,
 * а дальше выдаём собственные токены и работаем полностью самостоятельно.
 *
 * <p><b>Паттерн "find or create" (upsert):</b><br>
 * При первом входе через Google мы автоматически создаём аккаунт в нашей БД.
 * При повторных входах — находим существующий аккаунт по email.
 * Email при этом используется как уникальный идентификатор пользователя между системами.
 *
 * @see AuthenticationSuccessHandler
 * @see OAuth2User
 */
@Component
// @Component регистрирует этот класс как Spring Bean.
// SecurityConfig подключает его через oauth2Login().successHandler(oAuth2SuccessHandler).
@RequiredArgsConstructor
// Lombok генерирует конструктор для всех final-полей —
// Spring Security автоматически инжектирует их через конструктор.
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    /**
     * Репозиторий пользователей — для поиска существующего или сохранения нового.
     */
    private final UserRepository userRepository;

    /**
     * Провайдер JWT — для выдачи наших собственных access-токенов.
     */
    private final JwtTokenProvider tokenProvider;

    /**
     * Сервис управления refresh-токенами — для создания долгоживущего токена обновления.
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Jackson ObjectMapper — для сериализации ответа в JSON напрямую в тело HTTP-ответа.
     * Spring Boot автоматически конфигурирует его (регистрирует JavaTimeModule и т.д.),
     * поэтому инжектируем готовый бин, а не создаём новый.
     */
    private final ObjectMapper objectMapper;

    /**
     * Вызывается Spring Security после успешной OAuth2-аутентификации пользователя.
     *
     * <p>Метод выполняет следующую цепочку действий:
     * <ol>
     *   <li>Извлекает данные профиля из ответа OAuth2-провайдера (Google)</li>
     *   <li>Находит или создаёт пользователя в нашей БД (upsert по email)</li>
     *   <li>Выдаёт наши JWT access + refresh токены</li>
     *   <li>Отправляет токены клиенту в теле JSON-ответа</li>
     * </ol>
     *
     * <p><b>Формат ответа:</b><br>
     * Вместо стандартного редиректа (для SPA это неудобно) мы пишем JSON прямо в ответ.
     * Это позволяет фронтенду на React/Vue получить токены и сохранить их в localStorage.
     *
     * @param request        входящий HTTP-запрос (содержит параметры OAuth2 callback)
     * @param response       исходящий HTTP-ответ (в него пишем JSON с токенами)
     * @param authentication объект аутентификации от Spring Security с данными OAuth2User
     * @throws IOException если не удаётся записать ответ в response writer
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // Шаг 1: Получаем OAuth2User из объекта Authentication.
        // authentication.getPrincipal() возвращает Object — нам нужно привести к OAuth2User.
        // OAuth2User — интерфейс Spring Security для пользователя, пришедшего через OAuth2.
        // Он содержит атрибуты профиля, полученные от провайдера (Google, GitHub и т.д.).
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Шаг 2: Извлекаем атрибуты из профиля Google.
        // Имена атрибутов определяются стандартом OpenID Connect (OIDC),
        // который Google реализует поверх OAuth2:
        //   "email"       — адрес электронной почты (уникален, верифицирован Google)
        //   "given_name"  — имя (first name)
        //   "family_name" — фамилия (last name)
        // getAttribute() возвращает null, если атрибут отсутствует — защищаемся ниже.
        String email = oAuth2User.getAttribute("email");
        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");

        // Шаг 3: Ищем пользователя в БД или создаём нового — паттерн "find or create".
        // findByEmail().orElseGet() — если пользователь найден, используем его;
        // если нет (первый вход) — выполняем лямбду и создаём новую запись.
        // orElseGet() принимает Supplier<T> (лямбда без аргументов, возвращающая значение),
        // что эффективнее orElse() — создание объекта User происходит только при необходимости.
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // Создаём нового пользователя при первом входе через Google.
            // Builder-паттерн (Lombok @Builder на сущности User) делает создание читаемым.
            User newUser = User.builder()
                    .email(email)
                    // Защита от null: некоторые Google-аккаунты могут не возвращать имя.
                    // Пустая строка лучше null с точки зрения корректности БД и фронтенда.
                    .firstName(firstName != null ? firstName : "")
                    .lastName(lastName != null ? lastName : "")
                    // По умолчанию все OAuth2-пользователи получают обычную роль пользователя.
                    // Повышение до ADMIN происходит отдельно (вручную или через другой механизм).
                    .role(Role.ROLE_USER)
                    // Обратите внимание: поле password НЕ устанавливается.
                    // OAuth2-пользователи входят только через Google — пароль им не нужен.
                    // CustomUserDetailsService обрабатывает этот случай (возвращает пустую строку).
                    .build();
            // Сохраняем в БД и возвращаем сохранённую сущность (с заполненным id).
            return userRepository.save(newUser);
        });

        // Шаг 4: Генерируем наш собственный access-токен JWT.
        // Теперь пользователь аутентифицирован через Google — выдаём наши токены.
        // Это "federation": Google подтвердил личность, мы выдаём собственные credentials.
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // Шаг 5: Создаём refresh-токен — долгоживущий токен для обновления access-токена.
        // Refresh-токен хранится в БД (не в JWT), поэтому его можно инвалидировать.
        // Это важно для OAuth2-пользователей: при отзыве доступа Google нужно
        // иметь возможность разлогинить пользователя и у нас.
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Шаг 6: Формируем DTO ответа с токенами.
        // AuthResponse содержит оба токена и тип (Bearer — стандарт RFC 6750).
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer") // стандартный тип токена для HTTP Authorization header
                .build();

        // Шаг 7: Записываем JSON прямо в тело HTTP-ответа.
        // Устанавливаем заголовки ДО записи данных — иначе они будут проигнорированы
        // (HTTP-протокол требует, чтобы заголовки шли до тела).
        response.setContentType("application/json"); // указываем клиенту формат тела
        response.setCharacterEncoding("UTF-8");       // явная кодировка предотвращает проблемы с ?-символами

        // objectMapper.writeValue() сериализует объект в JSON и пишет в поток ответа.
        // Используем response.getWriter() — символьный поток, корректно работающий с UTF-8.
        // После этого вызова response считается "зафиксированным" — менять заголовки поздно.
        objectMapper.writeValue(response.getWriter(), authResponse);
    }
}
