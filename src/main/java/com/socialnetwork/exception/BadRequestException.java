package com.socialnetwork.exception;

/**
 * Исключение для некорректных запросов клиента (HTTP 400 Bad Request).
 *
 * <p>Бросается в сервисном слое при нарушении бизнес-правил:
 * попытке зарегистрироваться с уже занятым email, повторной отправке заявки в друзья,
 * попытке заблокировать уже заблокированного пользователя и т.д.
 *
 * <p>{@link GlobalExceptionHandler} перехватывает это исключение
 * и возвращает клиенту HTTP 400 с сообщением из конструктора.
 *
 * <p>Наследует {@link RuntimeException} — unchecked exception, не требует {@code throws} в сигнатуре.
 */
public class BadRequestException extends RuntimeException {

    /**
     * @param message описание нарушенного бизнес-правила (возвращается клиенту в ответе)
     */
    public BadRequestException(String message) {
        super(message);
    }
}
