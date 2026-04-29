package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO личного сообщения для передачи клиенту.
 *
 * <p>Возвращается при отправке ({@code POST /api/messages}) и при получении диалога
 * ({@code GET /api/messages/{userId}}). Содержит информацию об отправителе, получателе
 * и статус прочтения.
 */
@Data
@Builder
public class MessageResponse {
    /** Уникальный id сообщения. */
    private Long id;

    /** ID отправителя сообщения. */
    private Long senderId;

    /** Полное имя отправителя — для отображения в интерфейсе. */
    private String senderName;

    /** ID получателя сообщения. */
    private Long recipientId;

    /** Текст сообщения. */
    private String text;

    /** Флаг прочтения: {@code true} если получатель прочитал сообщение. */
    private boolean read;

    /** Дата и время отправки сообщения. */
    private LocalDateTime createdAt;

    /**
     * Преобразует сущность {@link Message} в DTO.
     * Вызывается внутри транзакции — обращение к lazy-полям sender и recipient безопасно.
     *
     * @param message сущность сообщения (с загруженными отправителем и получателем)
     * @return DTO сообщения
     */
    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFirstName() + " " + message.getSender().getLastName())
                .recipientId(message.getRecipient().getId())
                .text(message.getText())
                .read(message.isRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
