package com.socialnetwork.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Сервис управления refresh-токенами, хранящимися в Redis.
 *
 * <p><b>Что такое refresh-токен?</b><br>
 * Refresh-токен — долгоживущий токен (дни или недели), который позволяет
 * получить новый access-токен без повторного ввода логина и пароля.
 * В отличие от access-токена (JWT, stateless), refresh-токен хранится на сервере
 * в Redis — это позволяет его инвалидировать при logout или подозрительной активности.
 *
 * <p><b>Формат токена (передаётся клиенту):</b><br>
 * {@code {userId}:{uuid}} — строка из двух частей, разделённых двоеточием.<br>
 * Пример: {@code 42:550e8400-e29b-41d4-a716-446655440000}
 *
 * <p><b>Формат ключа в Redis:</b><br>
 * {@code refresh:{userId}:{uuid}} → значение {@code "{userId}"}<br>
 * Значение — это id пользователя в виде строки. Это позволяет получить userId
 * прямо из Redis без дополнительного запроса к БД.
 *
 * <p><b>Паттерн ротации токенов:</b><br>
 * При каждом обновлении пары токенов старый refresh-токен удаляется из Redis,
 * а вместо него создаётся новый. Если украденный токен будет использован повторно —
 * он уже не будет существовать в Redis и операция провалится.
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-поля redisTemplate
public class RefreshTokenService {

    // Префикс всех refresh-ключей в Redis для изоляции от других данных (blacklist, cache и т.д.)
    private static final String KEY_PREFIX = "refresh:";

    // Время жизни refresh-токена в миллисекундах, читается из application.yml
    // Типичное значение: 604_800_000 мс = 7 дней
    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // StringRedisTemplate — для работы с Redis в режиме строка → строка
    private final StringRedisTemplate redisTemplate;

    /**
     * Создаёт новый refresh-токен для пользователя и сохраняет его в Redis.
     *
     * <p>Формат токена, возвращаемого клиенту: {@code {userId}:{uuid}}<br>
     * Формат ключа в Redis: {@code refresh:{userId}:{uuid}} → {@code "{userId}"}
     *
     * <p>UUID в составе токена гарантирует уникальность — у одного пользователя
     * могут существовать несколько активных refresh-токенов одновременно
     * (например, если он зашёл с телефона и с ноутбука).
     *
     * @param userId id пользователя, для которого создаётся токен
     * @return строка токена в формате {@code "{userId}:{uuid}"} для передачи клиенту
     */
    public String createRefreshToken(Long userId) {
        // UUID.randomUUID() генерирует криптографически стойкий случайный идентификатор
        String tokenId = UUID.randomUUID().toString();

        // Ключ в Redis включает userId для удобства поиска всех токенов пользователя
        String redisKey = KEY_PREFIX + userId + ":" + tokenId;

        // Сохраняем в Redis: ключ → userId (строка), TTL = refreshTokenExpiration мс
        // Redis автоматически удалит запись по истечении TTL
        redisTemplate.opsForValue().set(redisKey, String.valueOf(userId), refreshTokenExpiration, TimeUnit.MILLISECONDS);

        // Возвращаем клиенту строку "{userId}:{uuid}" — это непрозрачный токен (opaque token)
        return userId + ":" + tokenId;
    }

    /**
     * Проверяет существование refresh-токена в Redis.
     *
     * <p>Токен считается валидным, если соответствующий ключ существует в Redis.
     * Истёкшие токены Redis удаляет автоматически — проверка TTL не требуется.
     *
     * @param rawToken строка токена в формате {@code "{userId}:{uuid}"}
     * @return {@code true} если токен существует и не истёк, {@code false} иначе
     */
    public boolean isValid(String rawToken) {
        // Разбиваем токен на части: ограничение split(2) — максимум 2 части
        // Это защита от токенов с несколькими двоеточиями в UUID
        String[] parts = rawToken.split(":", 2);
        if (parts.length != 2) return false; // Некорректный формат токена

        String redisKey = KEY_PREFIX + parts[0] + ":" + parts[1];

        // hasKey() возвращает null если Redis недоступен — Boolean.TRUE.equals() безопасен для null
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    /**
     * Извлекает id пользователя из refresh-токена через Redis.
     *
     * <p>Значение ключа в Redis — это строковое представление userId.
     * Метод используется для получения пользователя при обновлении токенов.
     *
     * @param rawToken строка токена в формате {@code "{userId}:{uuid}"}
     * @return id пользователя, или {@code null} если токен не найден
     */
    public Long getUserIdFromToken(String rawToken) {
        String[] parts = rawToken.split(":", 2);
        if (parts.length != 2) return null;

        // Получаем значение ключа из Redis (userId в виде строки)
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + parts[0] + ":" + parts[1]);

        // Конвертируем строку в Long; возвращаем null если ключ не найден
        return value != null ? Long.parseLong(value) : null;
    }

    /**
     * Удаляет конкретный refresh-токен из Redis (ротация при обновлении / logout).
     *
     * <p>Вызывается при обновлении пары токенов (refresh flow) и при logout.
     * После удаления повторное использование этого токена будет невозможно.
     *
     * @param rawToken строка токена для удаления
     */
    public void delete(String rawToken) {
        String[] parts = rawToken.split(":", 2);
        if (parts.length == 2) {
            // delete() удаляет ключ из Redis; если ключ не существует — команда игнорируется
            redisTemplate.delete(KEY_PREFIX + parts[0] + ":" + parts[1]);
        }
    }

    /**
     * Удаляет все refresh-токены пользователя из Redis.
     *
     * <p>Используется при принудительном logout всех сессий (например, при смене пароля)
     * или при блокировке пользователя администратором.
     *
     * <p><b>Важно:</b> {@code keys(pattern)} — операция O(N) в Redis, которая блокирует
     * сервер Redis при большом количестве ключей. В production следует использовать
     * SCAN вместо KEYS для нахождения ключей по паттерну.
     *
     * @param userId id пользователя, все токены которого нужно удалить
     */
    public void deleteAllForUser(Long userId) {
        // Паттерн: "refresh:{userId}:*" — все токены данного пользователя
        String pattern = KEY_PREFIX + userId + ":*";

        // keys() находит все ключи, соответствующие паттерну
        var keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            // Удаляем все найденные ключи одной командой для эффективности
            redisTemplate.delete(keys);
        }
    }
}
