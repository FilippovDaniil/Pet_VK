package com.socialnetwork.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Потребитель (consumer) событий дружбы из Apache Kafka.
 *
 * <p><b>Роль в архитектуре:</b><br>
 * Этот компонент — «другая сторона» паттерна Event-Driven: {@link FriendEventPublisher}
 * публикует события, а этот слушатель их получает и обрабатывает.
 * В текущей реализации обработка — только логирование (заглушка).
 * В реальном приложении здесь был бы вызов сервиса push-уведомлений,
 * WebSocket-рассылки, или записи в ленту активности.
 *
 * <p><b>Группа потребителей (Consumer Group):</b><br>
 * {@code groupId = "social-network-group"} — это идентификатор группы потребителей Kafka.
 * При нескольких экземплярах приложения Kafka распределяет партиции топика между ними,
 * гарантируя, что каждое сообщение обработается ровно одним экземпляром в группе.
 * Это основной механизм горизонтального масштабирования потребителей.
 *
 * <p><b>Десериализация:</b><br>
 * Spring Kafka использует Jackson для автоматической десериализации JSON из Kafka
 * в объект {@link FriendEvent}. Конфигурация десериализатора задаётся в {@code application.yml}.
 */
@Component          // Регистрирует класс как Spring Bean; Spring Kafka найдёт @KafkaListener
@Slf4j              // Lombok: создаёт logger для логирования полученных событий
public class FriendEventListener {

    /**
     * Обрабатывает входящее событие дружбы из Kafka.
     *
     * <p>{@code @KafkaListener} — аннотация Spring Kafka, которая:
     * <ol>
     *   <li>Подписывает метод на топик {@code "friend-events"}</li>
     *   <li>Автоматически десериализует JSON из Kafka в объект {@link FriendEvent}</li>
     *   <li>Вызывает метод в отдельном потоке при получении каждого сообщения</li>
     * </ol>
     *
     * <p>В текущей реализации — это заглушка (stub): событие только логируется.
     * В production здесь должна быть реальная отправка уведомления получателю.
     *
     * @param event десериализованное событие дружбы из Kafka
     */
    @KafkaListener(topics = "friend-events", groupId = "social-network-group")
    // topics: названия топиков для подписки (может быть несколько)
    // groupId: идентификатор consumer group — Kafka распределяет партиции между экземплярами приложения
    public void handleFriendEvent(FriendEvent event) {
        // Логируем полученное событие — в реальном приложении здесь была бы логика:
        // - отправка push-уведомления через Firebase/APNs
        // - WebSocket-уведомление через Spring WebSocket
        // - запись в таблицу уведомлений БД
        log.info("[NOTIFICATION] Friend event received: type={}, from user {} to user {}",
                event.getType(), event.getSourceUserId(), event.getTargetUserId());
    }
}
