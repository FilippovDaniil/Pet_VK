package com.socialnetwork.exception;

import com.socialnetwork.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений для всего REST API приложения.
 *
 * <p><b>Что такое @RestControllerAdvice?</b><br>
 * {@code @RestControllerAdvice} — специальный Spring-компонент, перехватывающий исключения,
 * выброшенные в любом {@code @RestController} или {@code @Controller} приложения.
 * Без него каждое необработанное исключение вернёт клиенту HTML-страницу ошибки Tomcat
 * (или стандартный JSON Spring Boot) — не очень информативно и непоследовательно.
 * С ним мы контролируем формат всех ответов об ошибках.
 *
 * <p><b>Принцип работы:</b><br>
 * Когда метод контроллера выбрасывает исключение, Spring ищет подходящий {@code @ExceptionHandler}
 * в классах с {@code @RestControllerAdvice}. Находит по типу исключения (или его родителям).
 * Методы-обработчики аннотированы {@code @ResponseStatus} для установки HTTP-кода ответа.
 *
 * <p><b>Иерархия обработчиков (от специфичного к общему):</b>
 * <ol>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found</li>
 *   <li>{@link BadRequestException} → 400 Bad Request</li>
 *   <li>{@link ForbiddenException} → 403 Forbidden</li>
 *   <li>{@link org.springframework.security.access.AccessDeniedException} → 403 Forbidden</li>
 *   <li>{@link org.springframework.security.core.AuthenticationException} → 401 Unauthorized</li>
 *   <li>{@link org.springframework.web.bind.MethodArgumentNotValidException} → 400 (валидация)</li>
 *   <li>{@link Exception} → 500 Internal Server Error (страховочный обработчик)</li>
 * </ol>
 */
@RestControllerAdvice
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
// Перехватывает исключения из всех контроллеров и возвращает JSON-ответ (не HTML).
@Slf4j
// Lombok: создаёт logger — нужен для логирования неожиданных (500) ошибок.
public class GlobalExceptionHandler {

    /**
     * Обрабатывает случаи, когда запрошенный ресурс не найден в базе данных.
     * Возвращает HTTP 404 Not Found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    // @ResponseStatus устанавливает HTTP-код ответа. Без него код был бы 200 OK по умолчанию.
    public ErrorResponse handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Обрабатывает некорректные запросы клиента: дублирование email, уже заблокирован и т.д.
     * Возвращает HTTP 400 Bad Request.
     */
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Обрабатывает попытки выполнить операцию без достаточных прав (например, удалить чужой пост).
     * Возвращает HTTP 403 Forbidden.
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Обрабатывает исключения Spring Security при попытке доступа к защищённому ресурсу
     * без нужной роли ({@code @PreAuthorize}).
     * Возвращает HTTP 403 Forbidden с общим сообщением (без деталей, которые нельзя раскрывать).
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // Намеренно не используем ex.getMessage() — сообщение Spring Security может быть техническим
        return buildError(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI());
    }

    /**
     * Обрабатывает ошибки аутентификации: неверный пароль, заблокированный аккаунт и т.д.
     * Возвращает HTTP 401 Unauthorized.
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Обрабатывает ошибки Bean Validation — когда поля запроса не проходят проверку
     * аннотациями {@code @NotBlank}, {@code @Email}, {@code @Size} и т.д.
     *
     * <p>Собирает сообщения всех нарушенных ограничений в одну строку через запятую,
     * чтобы клиент мог показать их все сразу (без повторных попыток угадать, что неправильно).
     *
     * @return HTTP 400 с перечислением всех ошибок валидации
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // getFieldErrors() возвращает список нарушений; getDefaultMessage() — сообщение из аннотации
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)       // берём сообщение из @NotBlank(message = "...")
                .collect(Collectors.joining(", "));       // объединяем через запятую
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    /**
     * Страховочный обработчик для всех непредвиденных исключений.
     *
     * <p>Логирует полный стек вызовов для диагностики, но возвращает клиенту
     * только общее сообщение "Internal server error" — детали ошибок сервера
     * не должны раскрываться клиентам по соображениям безопасности.
     *
     * @return HTTP 500 с обобщённым сообщением об ошибке
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex, HttpServletRequest request) {
        // Логируем с полным стеком (третий аргумент ex) — это критично для диагностики
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI());
    }

    /**
     * Вспомогательный метод: создаёт стандартный DTO ответа об ошибке.
     *
     * <p>Все ответы об ошибках имеют единый формат {@link ErrorResponse},
     * что упрощает обработку ошибок на стороне клиента.
     *
     * @param status  HTTP-статус ошибки
     * @param message читаемое сообщение об ошибке
     * @param path    URL-путь запроса, вызвавшего ошибку
     * @return DTO ответа об ошибке
     */
    private ErrorResponse buildError(HttpStatus status, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())       // время возникновения ошибки
                .status(status.value())               // числовой HTTP-код (404, 403 и т.д.)
                .error(status.getReasonPhrase())      // текстовое описание кода ("Not Found")
                .message(message)                     // конкретное сообщение об ошибке
                .path(path)                           // URL-путь запроса для диагностики
                .build();
    }
}
