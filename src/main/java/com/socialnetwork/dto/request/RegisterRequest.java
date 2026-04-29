package com.socialnetwork.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO запроса на регистрацию нового пользователя.
 *
 * <p>Аннотации Bean Validation ({@code @NotBlank}, {@code @Email}, {@code @Size})
 * проверяются Spring при наличии {@code @Valid} на параметре метода контроллера.
 * При нарушении ограничений выбрасывается {@code MethodArgumentNotValidException},
 * которую перехватывает {@link com.socialnetwork.exception.GlobalExceptionHandler}.
 *
 * <p>{@code @Data} — Lombok: геттеры, сеттеры, toString, equals, hashCode.
 */
@Data
public class RegisterRequest {

    /**
     * Email пользователя — будет использоваться как логин.
     * {@code @NotBlank} — не может быть null или пустой строкой.
     * {@code @Email} — должен соответствовать формату email-адреса (проверка регулярным выражением).
     */
    @NotBlank
    @Email
    private String email;

    /**
     * Пароль пользователя в открытом виде.
     * Будет захэширован BCrypt перед сохранением — никогда не хранится как есть.
     * {@code @Size(min = 6)} — минимум 6 символов для базовой безопасности.
     */
    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    /**
     * Имя пользователя (например, «Иван»).
     * {@code @NotBlank} — обязательное поле.
     * {@code @Size(max = 100)} — ограничение длины для хранения в БД.
     */
    @NotBlank
    @Size(max = 100)
    private String firstName;

    /**
     * Фамилия пользователя (например, «Иванов»).
     */
    @NotBlank
    @Size(max = 100)
    private String lastName;
}
