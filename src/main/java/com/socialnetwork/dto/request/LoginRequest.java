package com.socialnetwork.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса на аутентификацию пользователя (вход в систему).
 *
 * <p>Принимается эндпоинтом {@code POST /api/auth/login}.
 * Поля валидируются аннотациями Bean Validation при наличии {@code @Valid} в контроллере.
 */
@Data
public class LoginRequest {

    /**
     * Email пользователя — уникальный идентификатор/логин в системе.
     * {@code @Email} — проверяет формат email-адреса.
     */
    @NotBlank
    @Email
    private String email;

    /**
     * Пароль пользователя в открытом виде.
     * Будет сравнён с BCrypt-хэшем из базы данных через {@code passwordEncoder.matches()}.
     */
    @NotBlank
    private String password;
}
