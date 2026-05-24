package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на добавление комментария к посту.
 *
 * <p>Принимается эндпоинтом {@code POST /api/posts/{postId}/comments}.
 * Автор определяется из JWT-токена, пост — из пути URL.
 */
@Data
public class CommentCreateRequest {

    /**
     * Текст комментария.
     */
    @NotBlank
    @Size(max = 5000)
    private String text;
}
