package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на отправку личного сообщения.
 *
 * <p>Принимается эндпоинтом {@code POST /api/messages/{recipientId}}.
 * Отправитель определяется из JWT-токена, получатель — из пути URL.
 */
@Data
public class MessageRequest {

    /**
     * Текст сообщения.
     * Поле называется {@code content} для соответствия REST-конвенции и frontend-контракту.
     */
    @NotBlank
    @Size(max = 10000)
    private String content;
}
