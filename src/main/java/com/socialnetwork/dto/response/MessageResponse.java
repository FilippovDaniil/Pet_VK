package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long id;
    private Long senderId;
    private String senderName;
    private Long recipientId;
    private String text;
    private boolean read;
    private LocalDateTime createdAt;

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
