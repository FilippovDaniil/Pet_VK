package com.socialnetwork.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса на обновление пары токенов.
 *
 * <p>Принимается эндпоинтом {@code POST /api/auth/refresh}.
 * Клиент отправляет действующий refresh-токен и получает новую пару (access + refresh).
 */
@Data
public class RefreshTokenRequest {

    /**
     * Refresh-токен в формате {@code {userId}:{uuid}}.
     * {@code @NotBlank} — обязательное поле.
     */
    @NotBlank
    private String refreshToken;
}
