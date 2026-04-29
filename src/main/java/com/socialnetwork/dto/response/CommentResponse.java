package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Comment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO комментария к посту для передачи клиенту.
 *
 * <p>Содержит сводную информацию об авторе (id + полное имя) вместо вложенного объекта User.
 */
@Data
@Builder
public class CommentResponse {
    /** Уникальный id комментария. */
    private Long id;

    /** ID поста, к которому относится комментарий. */
    private Long postId;

    /** ID автора комментария — для перехода на профиль. */
    private Long authorId;

    /** Полное имя автора («Имя Фамилия») — для отображения в интерфейсе. */
    private String authorName;

    /** Текст комментария. */
    private String text;

    /** Дата и время написания комментария. */
    private LocalDateTime createdAt;

    /**
     * Преобразует сущность {@link Comment} в DTO.
     * Вызывается внутри транзакции, чтобы обращение к lazy-полям author и post было безопасным.
     *
     * @param comment сущность комментария (с загруженными автором и постом)
     * @return DTO комментария
     */
    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getFirstName() + " " + comment.getAuthor().getLastName())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
