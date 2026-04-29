package com.socialnetwork.config;

import com.socialnetwork.event.FriendEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Конфигурация Apache Kafka — брокера сообщений для асинхронного обмена событиями.
 *
 * <p><b>Что такое Kafka?</b><br>
 * Apache Kafka — распределённая платформа потоковой передачи данных.
 * Работает по принципу «публикация-подписка» (publish-subscribe):
 * один компонент публикует событие, другие — подписываются и обрабатывают его независимо.
 * Kafka хранит сообщения в топиках (topics), которые разбиты на партиции (partitions).
 *
 * <p><b>Зачем это нужно в данном проекте?</b><br>
 * При событиях дружбы (заявка отправлена, принята) нам нужно уведомить получателя.
 * Вместо синхронного HTTP-вызова к сервису уведомлений (жёсткая связанность),
 * мы публикуем событие в Kafka — это развязывает сервисы и повышает отказоустойчивость.
 *
 * <p><b>Управление топиками:</b><br>
 * Spring Kafka автоматически создаёт топики, объявленные как Beans типа {@link NewTopic},
 * при старте приложения (если топик ещё не существует в брокере).
 * Если топик уже есть — Bean просто игнорируется.
 *
 * <p><b>Подключение к Kafka:</b><br>
 * Адрес брокера настраивается в {@code application.yml} через
 * {@code spring.kafka.bootstrap-servers}. Spring Boot автоматически создаёт
 * {@code KafkaTemplate} и {@code KafkaListenerContainerFactory} на основе этих настроек.
 */
@Configuration
// @Configuration объявляет класс источником определений Spring Beans.
// Все методы, аннотированные @Bean, создадут объекты и зарегистрируют их в контексте Spring.
public class KafkaConfig {

    /**
     * Создаёт Kafka-топик {@code friend-events} для событий дружбы.
     *
     * <p>В этот топик {@link com.socialnetwork.event.FriendEventPublisher} отправляет
     * события о заявках в друзья, а {@link com.socialnetwork.event.FriendEventListener}
     * их получает и обрабатывает.
     *
     * <p>Параметры топика:
     * <ul>
     *   <li>{@code partitions(1)} — одна партиция: сообщения обрабатываются строго по порядку.
     *       Для горизонтального масштабирования увеличивают количество партиций.</li>
     *   <li>{@code replicas(1)} — один репликат: подходит для разработки/тестирования.
     *       В production используют минимум 3 реплики для отказоустойчивости.</li>
     * </ul>
     *
     * @return описание топика для автоматического создания Spring Kafka
     */
    @Bean
    public NewTopic friendEventsTopic() {
        return TopicBuilder.name("friend-events")
                .partitions(1)  // одна партиция — достаточно для небольшой нагрузки
                .replicas(1)    // одна реплика — только для локальной разработки
                .build();
    }

    /**
     * Создаёт Kafka-топик {@code post-events} для событий публикаций.
     *
     * <p>Зарезервирован для будущих событий (создание поста, лайк и т.д.).
     * В текущей версии топик создаётся, но потребители для него не реализованы.
     *
     * @return описание топика для автоматического создания
     */
    @Bean
    public NewTopic postEventsTopic() {
        return TopicBuilder.name("post-events")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
