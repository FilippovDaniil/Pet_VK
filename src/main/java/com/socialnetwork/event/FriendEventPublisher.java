package com.socialnetwork.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Публикатор событий дружбы в Apache Kafka.
 *
 * <p><b>Принцип работы:</b><br>
 * {@link com.socialnetwork.service.FriendService} вызывает {@link #publish(FriendEvent)}
 * после ключевых действий (отправка/принятие заявки). Этот компонент отправляет событие
 * в Kafka-топик {@code friend-events} асинхронно — без блокировки основного потока.
 *
 * <p><b>Ключ сообщения Kafka:</b><br>
 * В качестве ключа используется {@code eventId} (UUID). Kafka использует ключ для
 * определения раздела (partition): сообщения с одинаковым ключом попадают в один раздел
 * и обрабатываются в порядке поступления. UUID гарантирует равномерное распределение по разделам.
 *
 * <p><b>Асинхронность:</b><br>
 * {@code kafkaTemplate.send()} возвращает {@code CompletableFuture} — отправка происходит
 * в фоне. {@code whenComplete()} регистрирует callback для логирования результата.
 * Если Kafka недоступен — ошибка логируется, но НЕ прерывает основной бизнес-процесс
 * (уведомления не критичны для работы системы дружбы).
 */
@Component          // Регистрирует класс как Spring Bean общего назначения
@RequiredArgsConstructor // Lombok: конструктор для final-поля kafkaTemplate
@Slf4j              // Lombok: создаёт logger для логирования успеха/ошибок отправки
public class FriendEventPublisher {

    // Имя топика Kafka, в который публикуются все события дружбы
    // Топик создаётся через KafkaConfig при старте приложения
    private static final String TOPIC = "friend-events";

    // KafkaTemplate<ключ, значение> — главный инструмент отправки сообщений в Spring Kafka.
    // Параметр ключа String: ключ сообщения (eventId).
    // Параметр значения FriendEvent: тело сообщения (сериализуется в JSON через JsonSerializer).
    private final KafkaTemplate<String, FriendEvent> kafkaTemplate;

    /**
     * Публикует событие дружбы в Kafka-топик {@code friend-events}.
     *
     * <p>Метод неблокирующий: отправка происходит асинхронно.
     * Ошибки отправки логируются, но не прерывают выполнение вызывающего кода.
     *
     * @param event событие для публикации
     */
    public void publish(FriendEvent event) {
        // send(topic, key, value) — отправляет сообщение в Kafka.
        // Возвращает CompletableFuture, которое завершится после подтверждения брокером (или ошибки).
        kafkaTemplate.send(TOPIC, event.getEventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Ошибка отправки: Kafka недоступна или произошла сетевая ошибка.
                        // Логируем как ERROR, но не бросаем исключение — отправка уведомления
                        // не должна ломать основную бизнес-операцию (создание заявки).
                        log.error("Failed to publish friend event: {}", ex.getMessage());
                    } else {
                        // Успешная отправка: DEBUG чтобы не засорять логи при нормальной работе
                        log.debug("Published friend event: type={}, source={}, target={}",
                                event.getType(), event.getSourceUserId(), event.getTargetUserId());
                    }
                });
    }
}
