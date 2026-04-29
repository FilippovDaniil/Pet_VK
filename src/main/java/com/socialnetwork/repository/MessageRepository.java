package com.socialnetwork.repository;

import com.socialnetwork.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий личных сообщений ({@link Message}).
 *
 * <p>Стандартные CRUD-методы унаследованы от {@link JpaRepository}.
 * Два кастомных запроса реализованы через {@code @Query (JPQL)}, потому что
 * автогенерация по имени метода не поддерживает симметричные условия (A→B или B→A).
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Возвращает переписку между двумя пользователями — все сообщения в обоих направлениях,
     * отсортированные по убыванию даты (последние сначала).
     *
     * <p>Условие {@code (sender=1 AND recipient=2) OR (sender=2 AND recipient=1)} позволяет
     * получить диалог независимо от того, кто начал переписку. Имя метода не позволяет
     * выразить такую логику — используется явный JPQL.
     *
     * @param userId1  id первого участника диалога
     * @param userId2  id второго участника диалога
     * @param pageable параметры пагинации (страница, размер)
     * @return страница сообщений
     */
    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender.id = :userId1 AND m.recipient.id = :userId2) OR " +
           "(m.sender.id = :userId2 AND m.recipient.id = :userId1) " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findDialog(@Param("userId1") Long userId1, @Param("userId2") Long userId2, Pageable pageable);

    /**
     * Помечает все непрочитанные сообщения от {@code senderId} к {@code recipientId} как прочитанные.
     *
     * <p>{@code @Modifying} сообщает Spring Data, что это UPDATE-запрос, а не SELECT.
     * Без этой аннотации Spring выбросит исключение при попытке выполнить запрос,
     * изменяющий данные. Метод вызывается в {@code MessageService.getDialog} —
     * автоматически проставляет read=true, когда получатель открывает переписку.
     *
     * @param recipientId id получателя (тот, кто читает сообщения)
     * @param senderId    id отправителя (чьи сообщения помечаются как прочитанные)
     */
    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.recipient.id = :recipientId AND m.sender.id = :senderId AND m.read = false")
    void markAsRead(@Param("recipientId") Long recipientId, @Param("senderId") Long senderId);
}
