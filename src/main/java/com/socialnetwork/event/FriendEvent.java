package com.socialnetwork.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO события, связанного с дружбой, публикуемого в Apache Kafka.
 *
 * <p><b>Зачем события через Kafka?</b><br>
 * При отправке/принятии заявки в друзья нужно уведомить получателя.
 * Вместо синхронного вызова сервиса уведомлений (что создаёт жёсткую связанность),
 * мы публикуем событие в Kafka-топик {@code friend-events}. Любой сервис-потребитель
 * (уведомления, лента активности и т.д.) может подписаться на этот топик независимо.
 * Это паттерн «событийно-ориентированной архитектуры» (Event-Driven Architecture).
 *
 * <p><b>Типы событий:</b>
 * <ul>
 *   <li>{@code FRIEND_REQUEST_SENT} — заявка отправлена</li>
 *   <li>{@code FRIEND_REQUEST_ACCEPTED} — заявка принята</li>
 * </ul>
 *
 * <p><b>Аннотации Lombok:</b>
 * <ul>
 *   <li>{@code @Data} — генерирует геттеры, сеттеры, toString, equals, hashCode</li>
 *   <li>{@code @Builder} — Builder-паттерн для удобного создания объектов</li>
 *   <li>{@code @NoArgsConstructor} и {@code @AllArgsConstructor} — необходимы для
 *       десериализации Jackson при чтении из Kafka (Jackson требует конструктор без аргументов)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendEvent {

    /**
     * Уникальный идентификатор события (UUID).
     * Используется как ключ сообщения Kafka — гарантирует идемпотентность обработки
     * (дублирование сообщения можно определить по eventId).
     */
    private String eventId;

    /**
     * Время возникновения события.
     * Устанавливается в момент создания объекта в {@link #of(String, Long, Long)}.
     */
    private LocalDateTime timestamp;

    /**
     * Тип события: "FRIEND_REQUEST_SENT", "FRIEND_REQUEST_ACCEPTED" и т.д.
     * Потребители Kafka используют тип для маршрутизации обработки.
     */
    private String type;

    /**
     * ID пользователя, инициировавшего действие (отправил заявку или принял её).
     */
    private Long sourceUserId;

    /**
     * ID пользователя, на которого направлено действие (получатель заявки).
     */
    private Long targetUserId;

    /**
     * Фабричный метод для удобного создания события с автоматическим eventId и timestamp.
     *
     * <p>Используется в {@link com.socialnetwork.service.FriendService} вместо ручного
     * вызова Builder с заполнением всех полей.
     *
     * @param type         тип события (строковая константа)
     * @param sourceUserId id инициатора
     * @param targetUserId id цели
     * @return готовый объект события для публикации в Kafka
     */
    public static FriendEvent of(String type, Long sourceUserId, Long targetUserId) {
        return FriendEvent.builder()
                .eventId(UUID.randomUUID().toString()) // уникальный id для дедупликации
                .timestamp(LocalDateTime.now())        // фиксируем текущий момент
                .type(type)
                .sourceUserId(sourceUserId)
                .targetUserId(targetUserId)
                .build();
    }
}
