package com.socialnetwork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Точка входа приложения «Social Network» — VK-подобная социальная сеть.
 *
 * <p><b>@SpringBootApplication</b> — составная аннотация, объединяющая:
 * <ul>
 *   <li>{@code @Configuration} — класс является источником Bean-определений</li>
 *   <li>{@code @EnableAutoConfiguration} — Spring Boot автоматически настраивает
 *       компоненты на основе зависимостей в classpath (DataSource, Redis, Kafka и т.д.)</li>
 *   <li>{@code @ComponentScan} — сканирует пакет {@code com.socialnetwork} и все
 *       вложенные пакеты в поисках {@code @Component}, {@code @Service}, {@code @Repository} и т.д.</li>
 * </ul>
 *
 * <p><b>@EnableCaching</b> — активирует механизм кэширования Spring.
 * Без этой аннотации аннотации {@code @Cacheable} и {@code @CacheEvict} на методах сервисов
 * будут молча игнорироваться. (Дублируется с RedisConfig — одного вхождения достаточно.)
 *
 * <p><b>@EnableAsync</b> — включает поддержку асинхронного выполнения методов через аннотацию
 * {@code @Async}. Необходимо для корректной работы асинхронных callback-ов
 * (например, {@code whenComplete} у {@code KafkaTemplate}).
 */
@SpringBootApplication  // Сканирование компонентов + автоконфигурация Spring Boot
@EnableCaching          // Активирует @Cacheable, @CacheEvict и другие аннотации кэша
@EnableAsync            // Позволяет методам выполняться асинхронно через @Async
public class SocialNetworkApplication {

    /**
     * Запускает Spring Boot приложение.
     *
     * <p>{@link SpringApplication#run(Class, String[])} инициализирует контекст Spring,
     * запускает встроенный Tomcat-сервер, выполняет Liquibase-миграции
     * и регистрирует все Bean-ы.
     *
     * @param args аргументы командной строки (могут переопределять application.yml свойства)
     */
    public static void main(String[] args) {
        SpringApplication.run(SocialNetworkApplication.class, args);
    }
}
