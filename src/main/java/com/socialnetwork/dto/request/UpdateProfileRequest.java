package com.socialnetwork.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на обновление профиля пользователя.
 *
 * <p>Принимается эндпоинтом {@code PATCH /api/users/me}.
 * Все поля необязательны — клиент может передать только те, которые нужно изменить
 * (паттерн «частичное обновление»). Если поле не передано (null), оно не меняется.
 */
@Data
public class UpdateProfileRequest {

    /**
     * Новое имя пользователя. {@code null} если не нужно менять.
     * {@code @Size(max = 100)} — ограничение длины.
     */
    @Size(max = 100)
    private String firstName;

    /**
     * Новая фамилия пользователя. {@code null} если не нужно менять.
     */
    @Size(max = 100)
    private String lastName;

    /**
     * Новая биография («О себе»). {@code null} если не нужно менять.
     * Пустая строка ("") — допустима и означает «очистить биографию».
     */
    @Size(max = 500)
    private String bio;
}
