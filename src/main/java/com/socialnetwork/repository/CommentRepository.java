package com.socialnetwork.repository;

import com.socialnetwork.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий комментариев ({@link Comment}).
 *
 * <p>Spring Data JPA генерирует реализацию автоматически.
 * Кастомный метод строится по соглашению об именовании:
 * {@code findBy[PostId]OrderBy[CreatedAt][Asc]} → SELECT ... WHERE post_id=? ORDER BY created_at ASC.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Возвращает постраничный список комментариев к посту, отсортированных по дате создания
     * (от старых к новым — хронологический порядок, привычный для чатов и комментариев).
     *
     * <p>Имя метода расшифровывается так:
     * <ul>
     *   <li>{@code findBy} — SELECT запрос с условием</li>
     *   <li>{@code PostId} — WHERE post_id = :postId</li>
     *   <li>{@code OrderByCreatedAtAsc} — ORDER BY created_at ASC</li>
     * </ul>
     *
     * @param postId   id поста, чьи комментарии нужно получить
     * @param pageable параметры пагинации
     * @return страница комментариев
     */
    Page<Comment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);
}
