package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на отправку личного сообщения.
 *
 * <p>Принимается эндпоинтом {@code POST /api/messages}.
 * Отправитель определяется из JWT-токена в заголовке (не передаётся в теле запроса).
 */
@Data
public class MessageRequest {

    /**
     * ID получателя сообщения.
     * {@code @NotNull} — обязательное поле: нельзя отправить сообщение без указания получателя.
     * Используется {@code @NotNull}, а не {@code @NotBlank}, т.к. это числовое поле (Long), а не строка.
     */
    @NotNull
    private Long recipientId;

    /**
     * Текст сообщения.
     * {@code @NotBlank} — сообщение не может быть пустым.
     * {@code @Size(max = 10000)} — ограничение длины одного сообщения.
     */
    @NotBlank
    @Size(max = 10000)
    private String text;
}
