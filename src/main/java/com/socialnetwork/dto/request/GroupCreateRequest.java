package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на создание новой группы (сообщества).
 *
 * <p>Принимается эндпоинтом {@code POST /api/groups}.
 * Создатель автоматически становится владельцем (owner) и администратором группы.
 */
@Data
public class GroupCreateRequest {

    /**
     * Название группы — обязательное поле.
     * {@code @NotBlank} — не может быть пустым.
     * {@code @Size(max = 100)} — ограничение длины для корректного отображения в UI.
     */
    @NotBlank
    @Size(max = 100)
    private String name;

    /**
     * Описание группы (тема, правила, цели). Необязательное поле ({@code null} допускается).
     * {@code @Size(max = 2000)} — ограничение длины описания.
     */
    @Size(max = 2000)
    private String description;

    /**
     * URL аватара (логотипа) группы. Необязательное поле.
     * Хранится как ссылка на файл во внешнем хранилище.
     */
    private String avatarUrl;
}
