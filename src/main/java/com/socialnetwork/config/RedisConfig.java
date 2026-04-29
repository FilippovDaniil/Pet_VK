package com.socialnetwork.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Конфигурация Redis — хранилища данных в памяти для кэширования и управления токенами.
 *
 * <p><b>Зачем Redis в этом проекте?</b><br>
 * Redis используется для трёх целей:
 * <ol>
 *   <li><b>Кэш пользователей</b> — объекты User кэшируются с TTL 10 минут
 *       через аннотации {@code @Cacheable} / {@code @CacheEvict} в UserService.</li>
 *   <li><b>Refresh-токены</b> — хранятся как пары ключ-значение с TTL
 *       в {@link com.socialnetwork.service.RefreshTokenService}.</li>
 *   <li><b>Чёрный список JWT</b> — инвалидированные access-токены с TTL
 *       в {@link com.socialnetwork.service.BlacklistService}.</li>
 * </ol>
 *
 * <p><b>Сериализация:</b><br>
 * По умолчанию Spring Data Redis использует JDK-сериализацию (бинарный формат,
 * нечитаемый в Redis CLI). Мы заменяем её на JSON через Jackson, что позволяет
 * просматривать кэшированные данные в redis-cli и упрощает отладку.
 *
 * <p><b>@EnableCaching:</b><br>
 * Активирует поддержку аннотационного кэширования Spring ({@code @Cacheable},
 * {@code @CacheEvict}, {@code @CachePut}). Без этой аннотации аннотации кэша
 * на методах сервисов будут молча игнорироваться.
 */
@Configuration  // Класс содержит определения Spring Beans
@EnableCaching  // Включает поддержку аннотаций @Cacheable, @CacheEvict и т.д.
@RequiredArgsConstructor // Lombok: конструктор для objectMapper
public class RedisConfig {

    // Spring Boot автоматически настраивает ObjectMapper с поддержкой Java 8 Date/Time API
    // (JavaTimeModule), сериализацией enum и т.д. Используем готовый бин, а не создаём новый.
    private final ObjectMapper objectMapper;

    /**
     * Создаёт Jackson-сериализатор для Redis с копией общего ObjectMapper.
     *
     * <p>Используем {@code objectMapper.copy()} вместо {@code new ObjectMapper()},
     * чтобы унаследовать все настройки Spring Boot ObjectMapper (модули, features)
     * и при этом не мутировать общий бин (защита от side-effects).
     *
     * @return сериализатор объектов в JSON для записи в Redis
     */
    private GenericJackson2JsonRedisSerializer redisSerializer() {
        // copy() создаёт независимую копию с теми же настройками — изменения не затронут оригинал
        return new GenericJackson2JsonRedisSerializer(objectMapper.copy());
    }

    /**
     * Основной RedisTemplate для работы со сложными объектами (ключ String, значение Object).
     *
     * <p>Используется когда нужно хранить Java-объекты в Redis (сериализуются в JSON).
     * Настраиваем сериализаторы явно, чтобы данные в Redis были читаемы.
     *
     * @param factory фабрика Redis-соединений, автоматически создаётся Spring Boot
     * @return настроенный RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // StringRedisSerializer: ключи хранятся как UTF-8 строки (читаемы в redis-cli)
        template.setKeySerializer(new StringRedisSerializer());

        // GenericJackson2JsonRedisSerializer: значения хранятся в JSON-формате
        template.setValueSerializer(redisSerializer());

        // Аналогично для Hash-структур Redis (поля внутри хеша)
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(redisSerializer());

        // Инициализирует template после установки всех зависимостей (InitializingBean)
        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate для работы со строковыми значениями (ключ String, значение String).
     *
     * <p>Используется в {@link com.socialnetwork.service.BlacklistService} и
     * {@link com.socialnetwork.service.RefreshTokenService} — там данные хранятся
     * как простые строки (не JSON-объекты), что эффективнее.
     *
     * @param factory фабрика Redis-соединений
     * @return готовый StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        // StringRedisTemplate — готовая реализация с StringRedisSerializer для ключей и значений.
        // Не требует дополнительной настройки сериализаторов.
        return new StringRedisTemplate(factory);
    }

    /**
     * Менеджер кэша Spring, использующий Redis как backend.
     *
     * <p>Этот бин подключается к аннотациям {@code @Cacheable} / {@code @CacheEvict}.
     * Когда UserService вызывает {@code @Cacheable(value = "users")}, Spring
     * сохраняет результат через этот CacheManager в Redis.
     *
     * <p>Настройки кэша по умолчанию:
     * <ul>
     *   <li>TTL = 10 минут — данные автоматически устаревают и перечитываются из БД</li>
     *   <li>JSON-сериализация — кэш читаем и не зависит от версии Java-классов</li>
     *   <li>Null-значения не кэшируются — {@code disableCachingNullValues()} предотвращает
     *       кэширование отсутствующих ресурсов (cache stampede на несуществующих данных)</li>
     * </ul>
     *
     * @param factory фабрика Redis-соединений
     * @return настроенный RedisCacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))           // кэш живёт 10 минут
                .serializeKeysWith(                          // ключи кэша — читаемые строки
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(                        // значения кэша — JSON
                        RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer()))
                .disableCachingNullValues();                 // не кэшируем null (защита от ошибок)

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
