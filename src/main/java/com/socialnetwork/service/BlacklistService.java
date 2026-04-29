package com.socialnetwork.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Сервис чёрного списка JWT access-токенов.
 *
 * <p><b>Зачем нужен чёрный список?</b><br>
 * JWT-токены по природе stateless: сервер не хранит их состояние.
 * Даже после logout токен остаётся криптографически валидным до истечения {@code exp}.
 * Чтобы мгновенно «аннулировать» токен (например, при logout или смене пароля),
 * мы записываем его в Redis с оставшимся TTL. При каждом запросе
 * {@link com.socialnetwork.security.JwtAuthenticationFilter} проверяет этот список.
 *
 * <p><b>Формат ключа в Redis:</b><br>
 * {@code blacklist:jwt:<raw_token>} → значение {@code "1"} (произвольная метка наличия)<br>
 * TTL ключа = оставшееся время жизни токена. После истечения Redis сам удалит запись —
 * хранить токен дольше бессмысленно, т.к. просроченный токен и так отклоняется.
 *
 * <p><b>Производительность:</b><br>
 * Проверка наличия ключа в Redis — операция O(1). На практике это быстрее,
 * чем обращение к реляционной БД, что критично при проверке на каждый HTTP-запрос.
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-поля redisTemplate
public class BlacklistService {

    // Префикс всех ключей в Redis для namespace-изоляции — избегает коллизий с другими ключами
    private static final String KEY_PREFIX = "blacklist:jwt:";

    // StringRedisTemplate — специализированная версия RedisTemplate для строковых операций.
    // Настраивается в RedisConfig. Использует StringRedisSerializer — эффективнее GenericJackson2Json
    // для простых строковых значений.
    private final StringRedisTemplate redisTemplate;

    /**
     * Добавляет access-токен в чёрный список.
     *
     * <p>Токен сохраняется в Redis с указанным TTL. После его истечения Redis
     * автоматически удалит запись — хранить токен дольше нет смысла,
     * так как по истечении срока он и так не пройдёт проверку {@code validateToken()}.
     *
     * @param token     строка JWT access-токена (без префикса "Bearer ")
     * @param ttlMillis оставшееся время жизни токена в миллисекундах
     */
    public void blacklist(String token, long ttlMillis) {
        // Не добавляем в чёрный список уже просроченные токены (ttlMillis == 0 или отрицательный).
        // Такой токен в любом случае отклоняется validateToken() ещё до проверки чёрного списка.
        if (ttlMillis > 0) {
            // opsForValue().set(key, value, timeout, unit) — атомарная операция Redis SET с TTL.
            // "1" — произвольное значение-маркер (нам важен только факт наличия ключа, не значение).
            redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttlMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Проверяет, находится ли токен в чёрном списке.
     *
     * <p>Вызывается из {@link com.socialnetwork.security.JwtAuthenticationFilter}
     * при каждом запросе с JWT в заголовке Authorization.
     *
     * @param token строка JWT access-токена
     * @return {@code true} если токен аннулирован, {@code false} если токен активен
     */
    public boolean isBlacklisted(String token) {
        // hasKey() возвращает Boolean (оборачиваемый тип), который может быть null —
        // Boolean.TRUE.equals() безопасен для null и возвращает false в этом случае.
        // Если ключ не найден в Redis (токен не в чёрном списке) — возвращаем false.
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
