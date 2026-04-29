package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на создание или обновление поста.
 *
 * <p>Используется как при создании нового поста ({@code POST /api/posts/wall}),
 * так и при его редактировании ({@code PUT /api/posts/{id}}).
 */
@Data
public class PostCreateRequest {

    /**
     * Текст поста.
     * {@code @NotBlank} — обязательное поле, не может быть пустым.
     * {@code @Size(max = 10000)} — ограничение длины: 10 000 символов достаточно для
     * большинства постов и предотвращает злоупотребление.
     */
    @NotBlank
    @Size(max = 10000)
    private String text;

    /**
     * URL прикреплённого изображения.
     * Необязательное поле ({@code null} если изображения нет).
     * Хранится как ссылка на файл в хранилище, а не сами байты изображения.
     */
    private String imageUrl;
}
