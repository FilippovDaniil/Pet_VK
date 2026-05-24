package com.socialnetwork.dto.response;

import com.socialnetwork.entity.User;
import com.socialnetwork.search.UserDocument;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO публичного профиля пользователя.
 *
 * <p>Содержит данные, безопасные для передачи клиенту — без пароля и других секретов.
 * Возвращается всеми эндпоинтами, работающими с профилями: {@code /api/users}, {@code /api/friends}.
 *
 * <p>{@code @Data} — геттеры, сеттеры, toString, equals, hashCode (Lombok).<br>
 * {@code @Builder} — паттерн Builder для создания объектов.
 */
@Data
@Builder
public class UserResponse {
    /** Уникальный идентификатор пользователя в базе данных. */
    private Long id;

    /** Email пользователя (используется как логин). */
    private String email;

    /** Имя пользователя. */
    private String firstName;

    /** Фамилия пользователя. */
    private String lastName;

    /** URL аватара (ссылка на изображение). Может быть null. */
    private String avatarUrl;

    /** Биография пользователя (поле «О себе»). Может быть null. */
    private String bio;

    /** Строковое название роли: "ROLE_USER" или "ROLE_ADMIN". */
    private String role;

    /** Флаг блокировки: true — пользователь заблокирован администратором. */
    private boolean banned;

    /** Дата и время регистрации. */
    private LocalDateTime createdAt;

    /**
     * Статический фабричный метод: преобразует сущность {@link User} в DTO.
     *
     * <p>Паттерн «фабричный метод» (factory method): конвертация находится в DTO,
     * что удобнее, чем отдельный маппер — меньше кода, и DTO знает сам,
     * какие поля ему нужны из сущности.
     *
     * @param user сущность пользователя из базы данных
     * @return DTO с публичными полями профиля
     */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .role(user.getRole().name())
                .banned(user.isBanned())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static UserResponse fromDocument(UserDocument doc) {
        return UserResponse.builder()
                .id(Long.parseLong(doc.getId()))
                .email(doc.getEmail())
                .firstName(doc.getFirstName())
                .lastName(doc.getLastName())
                .avatarUrl(doc.getAvatarUrl())
                .banned(doc.isBanned())
                .build();
    }
}
