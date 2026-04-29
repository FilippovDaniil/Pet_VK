package com.socialnetwork.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Стандартный DTO ответа об ошибке.
 *
 * <p>Возвращается {@link com.socialnetwork.exception.GlobalExceptionHandler} при любых
 * ошибках обработки запроса. Единый формат ошибок упрощает их обработку на клиенте:
 * фронтенд знает, что любая ошибка имеет одинаковую структуру JSON.
 *
 * <p>Формат JSON-ответа при ошибке:
 * <pre>
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "User with id 42 not found",
 *   "path": "/api/users/42"
 * }
 * </pre>
 */
@Data
@Builder
public class ErrorResponse {
    /** Время возникновения ошибки (серверное). */
    private LocalDateTime timestamp;

    /** Числовой HTTP-код ошибки (400, 403, 404, 500 и т.д.). */
    private int status;

    /** Стандартное текстовое описание HTTP-кода ("Bad Request", "Not Found" и т.д.). */
    private String error;

    /** Конкретное сообщение об ошибке из исключения или бизнес-логики. */
    private String message;

    /** URL-путь запроса, который вызвал ошибку — для диагностики. */
    private String path;
}
