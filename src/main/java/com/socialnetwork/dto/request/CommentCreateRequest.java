package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на добавление комментария к посту.
 *
 * <p>Принимается эндпоинтом {@code POST /api/comments}.
 * Автор определяется из JWT-токена в заголовке Authorization.
 */
@Data
public class CommentCreateRequest {

    /**
     * ID поста, к которому добавляется комментарий.
     * {@code @NotNull} — обязательное поле (числовое, поэтому @NotNull, не @NotBlank).
     */
    @NotNull
    private Long postId;

    /**
     * Текст комментария.
     * {@code @NotBlank} — не может быть пустым.
     * {@code @Size(max = 5000)} — ограничение длины комментария (меньше чем у поста).
     */
    @NotBlank
    @Size(max = 5000)
    private String text;
}
