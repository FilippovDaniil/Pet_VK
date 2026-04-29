package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Post;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO публикации (поста) для передачи клиенту.
 *
 * <p>Используется в ответах контроллеров постов ({@code /api/posts}) и групп ({@code /api/groups}).
 * Содержит сводную информацию об авторе (id + полное имя) вместо вложенного объекта User,
 * что уменьшает объём передаваемых данных.
 */
@Data
@Builder
public class PostResponse {
    /** Уникальный id поста в базе данных. */
    private Long id;

    /** ID автора поста — для перехода на профиль. */
    private Long authorId;

    /** Полное имя автора: «Имя Фамилия» — для отображения в интерфейсе. */
    private String authorName;

    /** ID группы, если пост опубликован в группе. {@code null} для стеновых постов. */
    private Long groupId;

    /** Текстовое содержимое поста. */
    private String text;

    /** URL прикреплённого изображения. {@code null} если изображения нет. */
    private String imageUrl;

    /** Дата и время публикации поста. */
    private LocalDateTime createdAt;

    /** Дата и время последнего редактирования. */
    private LocalDateTime updatedAt;

    /**
     * Преобразует сущность {@link Post} в DTO.
     *
     * <p>Обратите внимание: метод обращается к {@code post.getAuthor()} и {@code post.getGroup()} —
     * это lazy-поля. Если метод вызывается вне активной транзакции Hibernate,
     * обращение к этим полям вызовет {@code LazyInitializationException}.
     * Поэтому вызов {@code PostResponse.from()} должен происходить внутри транзакции
     * (т.е. в методах, помеченных {@code @Transactional}).
     *
     * @param post сущность поста из базы данных (с загруженными author и group)
     * @return DTO поста
     */
    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthor().getId())
                // Формируем отображаемое имя прямо здесь — не нужен отдельный маппер
                .authorName(post.getAuthor().getFirstName() + " " + post.getAuthor().getLastName())
                // Тернарный оператор: если group не null — берём его id, иначе null (стеновой пост)
                .groupId(post.getGroup() != null ? post.getGroup().getId() : null)
                .text(post.getText())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
