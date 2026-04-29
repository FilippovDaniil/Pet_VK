package com.socialnetwork.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Провайдер JWT-токенов — центральный компонент системы аутентификации на основе токенов.
 *
 * <p><b>Что такое JWT?</b><br>
 * JWT (JSON Web Token) — компактный, самодостаточный способ передачи информации между сторонами
 * в виде JSON-объекта, подписанного цифровой подписью. Токен состоит из трёх частей,
 * разделённых точкой: {@code Header.Payload.Signature}. Подпись гарантирует, что содержимое
 * токена не было изменено после его выдачи сервером.
 *
 * <p><b>Почему JWT, а не сессии?</b><br>
 * Сессионный подход требует хранения состояния на сервере (в памяти или БД), что усложняет
 * горизонтальное масштабирование. JWT — stateless: сервер не хранит токены, вся информация
 * закодирована внутри самого токена и верифицируется подписью при каждом запросе.
 *
 * <p><b>Алгоритм HMAC-SHA:</b><br>
 * Используется симметричная подпись: один и тот же секретный ключ используется для создания
 * подписи и для её проверки. Это подходит для монолитов и микросервисов, если секрет не
 * передаётся наружу. Для распределённых систем с публичной верификацией лучше подходит RS256
 * (асимметричная пара ключей).
 *
 * @see io.jsonwebtoken.Jwts JJWT — основная библиотека для работы с JWT в Java
 * @see org.springframework.stereotype.Component
 */
@Component
// @Component регистрирует класс как Spring Bean в контексте приложения.
// Spring обнаружит его при сканировании пакетов (component scan) и создаст
// единственный экземпляр (singleton) на всё время жизни приложения.
@Slf4j
// @Slf4j — аннотация Lombok, которая автоматически генерирует поле:
//   private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
// Это избавляет от написания бойлерплейта и позволяет использовать log.debug()/log.warn() etc.
public class JwtTokenProvider {

    /**
     * Криптографический ключ для подписи и верификации JWT.
     * Тип {@link SecretKey} — это интерфейс JCA (Java Cryptography Architecture),
     * обёртка над байтовым массивом секрета. Поле final гарантирует, что ключ
     * устанавливается один раз при создании бина и не может быть изменён.
     */
    private final SecretKey secretKey;

    /**
     * Время жизни access-токена в миллисекундах, читается из конфига приложения.
     * Типично: 900_000 мс = 15 минут. Короткий TTL снижает риск компрометации
     * токена: даже украденный токен перестанет работать быстро.
     */
    private final long accessTokenExpiration;

    /**
     * Конструктор с внедрением зависимостей через параметры.
     *
     * <p>Почему конструктор, а не {@code @Value} на полях?<br>
     * Конструкторная инъекция делает зависимости явными и позволяет объявить поля
     * {@code final}, что гарантирует неизменяемость объекта после инициализации.
     * Это предпочтительный стиль в Spring начиная с версии 4.3.
     *
     * @param secret               секретная строка из {@code application.yml} (свойство
     *                             {@code app.jwt.secret}). Должна быть достаточно длинной
     *                             (минимум 32 байта для HMAC-SHA256).
     * @param accessTokenExpiration время жизни access-токена в мс из
     *                              {@code app.jwt.access-token-expiration}.
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            // @Value("${...}") — Spring считывает значение из application.yml/properties
            // и подставляет его в параметр конструктора до создания бина.
            // Если свойство отсутствует — при старте приложения выбрасывается исключение.
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration) {

        // Keys.hmacShaKeyFor() из библиотеки JJWT преобразует байтовый массив секрета
        // в объект SecretKey, пригодный для алгоритма HMAC-SHA.
        // JJWT автоматически выберет SHA-256, SHA-384 или SHA-512 в зависимости от длины ключа.
        // Используем UTF-8, чтобы кодирование строки было предсказуемым на всех платформах.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        this.accessTokenExpiration = accessTokenExpiration;
    }

    /**
     * Генерирует подписанный access-токен JWT для аутентифицированного пользователя.
     *
     * <p>Структура claims (полезная нагрузка токена):<br>
     * <ul>
     *   <li>{@code sub} (subject) — email пользователя, стандартный claim RFC 7519</li>
     *   <li>{@code userId} — внутренний ID, нужен чтобы не делать запрос в БД при каждом обращении</li>
     *   <li>{@code role} — роль пользователя для проверки прав доступа без БД</li>
     *   <li>{@code iat} (issuedAt) — время выдачи, стандартный claim</li>
     *   <li>{@code exp} (expiration) — время истечения, стандартный claim</li>
     * </ul>
     *
     * @param userId ID пользователя в базе данных
     * @param email  email, используется как subject токена
     * @param role   строковое название роли (например, "ROLE_USER")
     * @return компактная строка JWT вида {@code xxxxx.yyyyy.zzzzz}
     */
    public String generateAccessToken(Long userId, String email, String role) {
        Date now = new Date(); // текущий момент — используется как время выдачи токена (iat)

        // Время истечения = текущее время + настроенный TTL.
        // После этого момента токен будет отклоняться парсером как просроченный.
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                // subject — стандартный claim "кому выдан токен", обычно идентификатор пользователя.
                // Здесь используем email, т.к. он уникален и удобен для loadUserByUsername().
                .subject(email)

                // Кастомные claims — произвольные пары ключ-значение в payload токена.
                // userId позволяет не обращаться к БД лишний раз при каждом запросе.
                .claim("userId", userId)

                // role кодируется прямо в токене для быстрой авторизации без БД.
                // Минус: при смене роли старые токены продолжат нести устаревшую роль до истечения TTL.
                .claim("role", role)

                .issuedAt(now)       // claim "iat" — время выдачи (issued at)
                .expiration(expiryDate) // claim "exp" — время истечения (expiration)

                // Подписываем токен секретным ключом.
                // JJWT автоматически выбирает алгоритм (HS256/384/512) по длине ключа.
                .signWith(secretKey)

                // compact() сериализует токен в компактную строку Base64url(header) + "." +
                // Base64url(payload) + "." + Base64url(signature).
                .compact();
    }

    /**
     * Извлекает email пользователя из токена (claim "sub" / subject).
     *
     * @param token строка JWT
     * @return email пользователя
     */
    public String getEmailFromToken(String token) {
        // getSubject() возвращает стандартный claim "sub", который мы установили как email.
        return parseClaims(token).getSubject();
    }

    /**
     * Извлекает ID пользователя из кастомного claim "userId".
     *
     * @param token строка JWT
     * @return ID пользователя в базе данных
     */
    public Long getUserIdFromToken(String token) {
        // get(name, Class) автоматически приводит значение claim к нужному типу.
        // JWT хранит числа как Integer, поэтому JJWT выполняет безопасное приведение к Long.
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * Извлекает строковое название роли из кастомного claim "role".
     *
     * @param token строка JWT
     * @return строка роли, например "ROLE_USER" или "ROLE_ADMIN"
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Возвращает оставшееся время жизни токена в миллисекундах.
     *
     * <p>Используется при logout: перед тем как добавить токен в чёрный список Redis,
     * нужно знать на какое время его туда класть. Хранить его дольше, чем его TTL —
     * бессмысленно, т.к. просроченный токен и так будет отклонён валидатором.
     *
     * @param token строка JWT
     * @return оставшееся время в мс; 0, если токен уже истёк (защита от отрицательных значений)
     */
    public long getRemainingTtlMillis(String token) {
        Date expiry = parseClaims(token).getExpiration(); // извлекаем дату истечения из claim "exp"

        // Разность между временем истечения и текущим временем — это оставшийся TTL.
        long remaining = expiry.getTime() - System.currentTimeMillis();

        // Math.max(..., 0) гарантирует, что мы не вернём отрицательное значение
        // в редком случае гонки (token истёк между парсингом и этой строкой).
        return Math.max(remaining, 0);
    }

    /**
     * Проверяет токен на корректность: подпись, срок действия, формат.
     *
     * <p>Метод делегирует всю логику валидации в {@link #parseClaims(String)}.
     * Любое нарушение (неверная подпись, истёкший срок, повреждённая структура)
     * вызывает исключение, которое мы перехватываем и возвращаем {@code false}.
     *
     * @param token строка JWT для проверки
     * @return {@code true} если токен валиден, {@code false} в противном случае
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token); // при любой ошибке выбросит JwtException
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException — базовый класс для всех ошибок JJWT:
            //   ExpiredJwtException — токен просрочен
            //   SignatureException — подпись не совпадает (токен подделан)
            //   MalformedJwtException — некорректный формат
            //   UnsupportedJwtException — неподдерживаемый тип токена
            // IllegalArgumentException — токен null или пустая строка
            // Логируем на уровне DEBUG, чтобы не засорять логи при нормальной работе.
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Внутренний метод для парсинга и верификации JWT.
     *
     * <p>Является единой точкой входа для всех операций чтения токена.
     * Если токен невалиден — выбрасывает исключение, что позволяет
     * публичным методам использовать его в блоке try-catch.
     *
     * @param token строка JWT
     * @return объект {@link Claims} с payload токена (все claims)
     * @throws JwtException если токен невалиден по любой причине
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                // Указываем ключ для верификации подписи.
                // Парсер проверит, что подпись токена соответствует именно этому ключу.
                .verifyWith(secretKey)
                .build()
                // parseSignedClaims() — парсит только подписанные JWS-токены.
                // Одновременно проверяет подпись И срок действия (claim "exp").
                // Если что-то не так — выбрасывает соответствующее JwtException.
                .parseSignedClaims(token)
                // getPayload() возвращает Claims — Map-подобный объект со всеми claims токена.
                .getPayload();
    }
}
