package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Group;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO группы (сообщества) для передачи клиенту.
 *
 * <p>Возвращается при создании группы и при запросе её информации.
 * Содержит сводные данные о владельце (id + полное имя).
 */
@Data
@Builder
public class GroupResponse {
    /** Уникальный id группы. */
    private Long id;

    /** Название группы. */
    private String name;

    /** Описание группы (тема, правила). Может быть null. */
    private String description;

    /** URL аватара (логотипа) группы. Может быть null. */
    private String avatarUrl;

    /** ID владельца группы. */
    private Long ownerId;

    /** Полное имя владельца — для отображения в интерфейсе. */
    private String ownerName;

    /** Дата и время создания группы. */
    private LocalDateTime createdAt;

    /**
     * Преобразует сущность {@link Group} в DTO.
     * Обращается к lazy-полю {@code owner} — вызывается только внутри транзакции.
     *
     * @param group сущность группы (с загруженным владельцем)
     * @return DTO группы
     */
    public static GroupResponse from(Group group) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .avatarUrl(group.getAvatarUrl())
                .ownerId(group.getOwner().getId())
                .ownerName(group.getOwner().getFirstName() + " " + group.getOwner().getLastName())
                .createdAt(group.getCreatedAt())
                .build();
    }
}
