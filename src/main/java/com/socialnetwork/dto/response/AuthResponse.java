package com.socialnetwork.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа на запросы аутентификации (регистрация, вход, обновление токена).
 *
 * <p>Возвращается клиенту при успешной аутентификации.
 * Содержит все данные, необходимые для дальнейших запросов к API.
 *
 * <p>{@code @Data} — Lombok: геттеры, сеттеры, toString, equals, hashCode.<br>
 * {@code @Builder} — позволяет создавать объект через {@code AuthResponse.builder()...build()}.<br>
 * {@code @NoArgsConstructor} / {@code @AllArgsConstructor} — необходимы для сериализации/десериализации Jackson.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * JWT access-токен для аутентификации последующих запросов.
     * Передаётся в каждом запросе в заголовке: {@code Authorization: Bearer <accessToken>}.
     * Короткоживущий — истекает через {@code expiresIn} секунд.
     */
    private String accessToken;

    /**
     * Refresh-токен для обновления пары токенов без повторного входа.
     * Долгоживущий (дни/недели). Хранится клиентом в защищённом месте.
     * Передаётся в запрос {@code POST /api/auth/refresh} при истечении access-токена.
     */
    private String refreshToken;

    /**
     * Тип токена согласно RFC 6750.
     * Всегда "Bearer" — клиент должен добавлять этот префикс в заголовок Authorization.
     */
    private String tokenType = "Bearer";

    /**
     * Время жизни access-токена в секундах с момента выдачи.
     * Типичное значение: 900 (15 минут). После истечения нужно обновить токен через /refresh.
     */
    private long expiresIn;
}
