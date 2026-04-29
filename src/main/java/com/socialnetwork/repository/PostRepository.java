package com.socialnetwork.repository;

import com.socialnetwork.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с постами пользователей и групп.
 *
 * <p>Методы используют соглашение об именовании Spring Data JPA:
 * Spring автоматически генерирует SQL-запросы из названий методов.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Возвращает посты стены пользователя с пагинацией (от новых к старым).
     *
     * <p>Имя метода декодируется Spring Data в JPQL:
     * {@code WHERE author.id = :authorId AND group IS NULL ORDER BY createdAt DESC}
     *
     * <p>{@code GroupIsNull} — ключевое условие: отбирает только «стеновые» посты,
     * исключая посты, опубликованные в группах (у которых group != null).
     *
     * @param authorId id автора (владельца стены)
     * @param pageable параметры пагинации
     * @return страница со стеновыми постами автора
     */
    Page<Post> findByAuthorIdAndGroupIsNullOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    /**
     * Возвращает посты группы с пагинацией (от новых к старым).
     *
     * <p>Имя метода декодируется Spring Data в JPQL:
     * {@code WHERE group.id = :groupId ORDER BY createdAt DESC}
     *
     * @param groupId  id группы
     * @param pageable параметры пагинации
     * @return страница с постами группы
     */
    Page<Post> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
}
