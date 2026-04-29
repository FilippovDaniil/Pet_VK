package com.socialnetwork.exception;

/**
 * Исключение для случаев, когда запрошенный ресурс не найден в базе данных.
 *
 * <p>Бросается в сервисном слое через {@code orElseThrow()}, когда Optional пуст.
 * {@link GlobalExceptionHandler} перехватывает это исключение и возвращает клиенту
 * HTTP 404 Not Found с понятным сообщением.
 *
 * <p>Наследует {@link RuntimeException} — не требует объявления в сигнатуре метода
 * через {@code throws}, что упрощает код (unchecked exception).
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Конструктор с произвольным сообщением.
     * Используется когда стандартный формат "X with id Y not found" не подходит.
     *
     * @param message текст сообщения об ошибке
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Удобный конструктор для стандартного сообщения "{resource} with id {id} not found".
     * Пример: {@code new ResourceNotFoundException("User", 42L)} → "User with id 42 not found"
     *
     * @param resource название сущности (User, Post, Comment и т.д.)
     * @param id       числовой идентификатор не найденной сущности
     */
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " with id " + id + " not found");
    }
}
