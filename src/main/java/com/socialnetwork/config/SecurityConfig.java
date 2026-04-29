package com.socialnetwork.config;

import com.socialnetwork.security.JwtAuthenticationFilter;
import com.socialnetwork.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Центральная конфигурация Spring Security для всего приложения.
 *
 * <p><b>Архитектура безопасности приложения:</b><br>
 * Приложение использует два механизма аутентификации параллельно:
 * <ol>
 *   <li><b>JWT</b> — для REST API. Клиент (мобильное приложение, SPA) получает токен
 *       при логине и передаёт его в каждом запросе через заголовок Authorization.</li>
 *   <li><b>OAuth2 (Google)</b> — для входа через социальные сети. После успешной
 *       аутентификации Google наш {@link OAuth2SuccessHandler} выдаёт те же JWT-токены.</li>
 * </ol>
 *
 * <p><b>Что такое Filter Chain?</b><br>
 * Spring Security реализует защиту через цепочку Servlet-фильтров. Каждый HTTP-запрос
 * проходит через фильтры по порядку. Фильтры могут:
 * <ul>
 *   <li>Прервать цепочку и вернуть ответ (401, 403)</li>
 *   <li>Обогатить контекст (установить Authentication) и передать запрос дальше</li>
 * </ul>
 * Мы конфигурируем порядок и поведение этих фильтров через {@link SecurityFilterChain}.
 *
 * <p><b>Ключевые решения этой конфигурации:</b>
 * <ul>
 *   <li>CSRF отключён — REST API не использует браузерные cookies для аутентификации</li>
 *   <li>Сессии отключены (STATELESS) — JWT сам несёт всё необходимое состояние</li>
 *   <li>CORS настроен глобально — разрешены запросы с любых origins</li>
 * </ul>
 */
@Configuration
// @Configuration объявляет класс источником определений Spring Beans (методы с @Bean).
// Под капотом Spring создаёт CGLIB-прокси этого класса, чтобы @Bean-методы
// возвращали один и тот же singleton при повторных вызовах.
@EnableWebSecurity
// @EnableWebSecurity активирует инфраструктуру Spring Security:
// регистрирует DelegatingFilterProxy, подключает SecurityFilterChain и т.д.
// В Spring Boot это часто включается автоматически, но явное указание
// документирует намерение и гарантирует правильный порядок инициализации.
@EnableMethodSecurity
// @EnableMethodSecurity включает аннотации безопасности на уровне методов:
//   @PreAuthorize("hasRole('ADMIN')")  — проверка до вызова метода
//   @PostAuthorize("...")              — проверка после вызова (доступ к returnObject)
//   @Secured({"ROLE_USER"})           — упрощённая форма
// Без этой аннотации такие аннотации на сервисах будут молча игнорироваться!
@RequiredArgsConstructor
// Lombok генерирует конструктор для final-полей — Spring инжектирует их автоматически.
public class SecurityConfig {

    /**
     * Наш кастомный фильтр JWT: извлекает токен из заголовка и устанавливает Authentication.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Обработчик успешного OAuth2-входа: создаёт/находит пользователя и выдаёт JWT.
     */
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    /**
     * Главный Bean конфигурации — определяет правила фильтрации всех HTTP-запросов.
     *
     * <p>Spring Security поддерживает несколько SecurityFilterChain в одном приложении.
     * Каждый имеет приоритет (order) и набор URL-шаблонов. Здесь мы определяем единственную
     * цепочку для всего приложения.
     *
     * @param http строитель конфигурации безопасности, предоставляемый Spring Security
     * @return сконфигурированная цепочка фильтров безопасности
     * @throws Exception если конфигурация содержит ошибки
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // --- Отключение CSRF-защиты ---
            // CSRF (Cross-Site Request Forgery) — атака, при которой вредоносный сайт
            // заставляет браузер пользователя отправить запрос к нашему серверу.
            // CSRF-защита актуальна для браузерных приложений с cookie-аутентификацией:
            // браузер автоматически отправляет cookies, и сервер не может отличить
            // легитимный запрос от атаки.
            // Для REST API с JWT в заголовке Authorization CSRF неактуален:
            // JavaScript на стороннем сайте не может добавить наш токен в заголовок
            // из-за политики Same-Origin Policy браузера.
            .csrf(AbstractHttpConfigurer::disable)

            // --- Настройка CORS ---
            // CORS (Cross-Origin Resource Sharing) — механизм браузера, контролирующий
            // запросы к другому домену/порту. Браузер сначала отправляет preflight
            // (OPTIONS-запрос), и если сервер разрешает — выполняет основной запрос.
            // Мы подключаем нашу кастомную конфигурацию CORS (метод ниже),
            // чтобы централизованно управлять разрешёнными origins.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // --- Отключение HTTP-сессий ---
            // STATELESS означает: Spring Security никогда не создаёт и не использует
            // HTTP-сессию для хранения SecurityContext.
            // При каждом запросе аутентификация восстанавливается заново из JWT.
            // Это критично для горизонтального масштабирования: любой экземпляр сервера
            // может обработать запрос, не зная о предыдущих запросах от этого пользователя.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // --- Правила авторизации запросов ---
            // Правила применяются В ПОРЯДКЕ ОБЪЯВЛЕНИЯ — первое совпавшее правило побеждает.
            // Поэтому более специфичные правила должны идти ПЕРЕД более общими.
            .authorizeHttpRequests(auth -> auth

                // Preflight OPTIONS-запросы должны проходить без аутентификации.
                // Браузер отправляет OPTIONS перед "сложными" CORS-запросами (с Authorization).
                // Если мы требуем токен для OPTIONS — браузер не сможет выполнить preflight
                // и основной запрос никогда не дойдёт до сервера.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Публичные эндпоинты — доступны без аутентификации:
                .requestMatchers(
                    "/",                    // корень — для редиректа или статики
                    "/index.html",          // главная страница SPA
                    "/api/auth/**",         // login, register, refresh — токена ещё нет
                    "/oauth2/**",           // точки входа OAuth2 (Google redirect)
                    "/login/**",            // вспомогательные страницы Spring OAuth2
                    "/swagger-ui/**",       // UI документации API
                    "/swagger-ui.html",     // альтернативный URL Swagger UI
                    "/v3/api-docs/**",      // OpenAPI спецификация (JSON/YAML)
                    "/uploads/**"           // загруженные файлы (аватары, изображения)
                ).permitAll()

                // Административные эндпоинты — только для пользователей с ролью ADMIN.
                // hasRole("ADMIN") проверяет наличие authority "ROLE_ADMIN" (префикс добавляется автоматически).
                // Если пользователь не аутентифицирован — вернётся 401 Unauthorized.
                // Если аутентифицирован, но нет роли — вернётся 403 Forbidden.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // GET-запросы к публичному контенту требуют аутентификации.
                // Разделяем по HTTP-методу: чтение (GET) — требует входа,
                // но изменение (POST/PUT/DELETE) будет покрыто правилом anyRequest() ниже.
                .requestMatchers(HttpMethod.GET, "/api/posts/**", "/api/groups/**", "/api/comments/**")
                    .authenticated()

                // Все остальные запросы требуют аутентификации.
                // anyRequest() должен быть ПОСЛЕДНИМ правилом — иначе перекроет все последующие.
                .anyRequest().authenticated()
            )

            // --- Встраивание JWT-фильтра в цепочку ---
            // addFilterBefore(filter, position) вставляет наш фильтр ПЕРЕД указанной позицией.
            // UsernamePasswordAuthenticationFilter — стандартный фильтр обработки формы логина.
            // Мы хотим обработать JWT ДО него, чтобы к моменту его работы SecurityContext
            // уже был заполнен. Если JWT невалиден — фильтр ничего не делает,
            // и управление передаётся дальше по цепочке.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // --- Конфигурация OAuth2 Login ---
            // Подключаем обработку входа через внешних OAuth2-провайдеров (Google и т.д.).
            // Spring Security автоматически создаёт эндпоинты:
            //   GET /oauth2/authorization/{registrationId} — начало OAuth2 flow (редирект к провайдеру)
            //   GET /login/oauth2/code/{registrationId}   — callback после аутентификации у провайдера
            // successHandler — вызывается Spring Security после успешной аутентификации.
            // Наш OAuth2SuccessHandler выдаёт JWT и записывает их в ответ.
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            );

        // build() финализирует конфигурацию и создаёт объект SecurityFilterChain.
        // После этого вызова конфигурацию нельзя изменить.
        return http.build();
    }

    /**
     * Bean для хеширования паролей с использованием алгоритма BCrypt.
     *
     * <p><b>Почему BCrypt?</b><br>
     * BCrypt специально разработан для хеширования паролей:
     * <ul>
     *   <li>Медленный по дизайну — затрудняет брутфорс (регулируется параметром "strength")</li>
     *   <li>Встроенная соль (salt) — каждый хеш уникален, Rainbow Tables бесполезны</li>
     *   <li>Адаптивный — strength можно увеличивать с ростом мощности железа</li>
     * </ul>
     * Стандартный strength (сложность) = 10, что означает 2^10 = 1024 итерации хеширования.
     *
     * <p>Spring Security автоматически использует этот Bean при проверке пароля
     * при аутентификации через форму логина.
     *
     * @return экземпляр BCryptPasswordEncoder со стандартной сложностью
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCryptPasswordEncoder без параметров использует strength=10.
        // Увеличение до 12-14 рекомендуется для production-систем с высокими требованиями безопасности.
        return new BCryptPasswordEncoder();
    }

    /**
     * Предоставляет {@link AuthenticationManager} как Spring Bean.
     *
     * <p><b>Зачем это нужно?</b><br>
     * AuthenticationManager — центральный интерфейс аутентификации Spring Security.
     * В нашем AuthController мы внедряем его напрямую, чтобы программно аутентифицировать
     * пользователя при обычном логине (email + пароль). Spring Security создаёт
     * AuthenticationManager автоматически, но не регистрирует его как Bean по умолчанию.
     * Этот метод "вытаскивает" его из конфигурации и делает доступным для инъекции.
     *
     * @param config конфигурация аутентификации Spring Security (инжектируется автоматически)
     * @return готовый AuthenticationManager
     * @throws Exception если не удаётся получить менеджер из конфигурации
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // getAuthenticationManager() возвращает настроенный AuthenticationManager,
        // который знает о нашем CustomUserDetailsService и PasswordEncoder.
        return config.getAuthenticationManager();
    }

    /**
     * Конфигурация CORS для всего приложения.
     *
     * <p><b>Почему это важно:</b><br>
     * Браузеры по умолчанию блокируют Ajax-запросы к другому домену (другой origin).
     * Чтобы React/Vue фронтенд на localhost:3000 мог обращаться к нашему API на localhost:8080,
     * сервер должен явно разрешить это через CORS-заголовки.
     *
     * <p><b>Внимание — производительность и безопасность:</b><br>
     * {@code setAllowedOriginPatterns(List.of("*"))} разрешает запросы с ЛЮБОГО origin.
     * Это удобно для разработки, но в production следует ограничить конкретными доменами.
     * При {@code allowCredentials(true)} нельзя использовать {@code setAllowedOrigins("*")} —
     * только {@code setAllowedOriginPatterns("*")} или конкретные origins.
     *
     * @return источник CORS-конфигурации, сопоставленный с URL-шаблонами
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Разрешаем запросы с любого origin (домена/порта/протокола).
        // setAllowedOriginPatterns поддерживает wildcards (setAllowedOrigins — нет).
        // В production замените на конкретные домены: List.of("https://myvkapp.com")
        config.setAllowedOriginPatterns(List.of("*"));

        // Разрешённые HTTP-методы. PATCH используется для частичного обновления ресурса.
        // OPTIONS нужен для preflight — браузер проверяет разрешения перед основным запросом.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Разрешаем все заголовки запроса (включая Authorization, Content-Type и кастомные).
        // В production можно ограничить: List.of("Authorization", "Content-Type")
        config.setAllowedHeaders(List.of("*"));

        // allowCredentials(true) разрешает браузеру отправлять cookies, Authorization-заголовок
        // и TLS-сертификаты клиента в кросс-доменных запросах.
        // Это обязательно для Bearer-аутентификации из браузера.
        config.setAllowCredentials(true);

        // UrlBasedCorsConfigurationSource сопоставляет CORS-конфигурацию с URL-паттернами.
        // "/**" означает: применять эту конфигурацию ко всем URL приложения.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
