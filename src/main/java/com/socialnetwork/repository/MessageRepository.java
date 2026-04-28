package com.socialnetwork.repository;

import com.socialnetwork.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender.id = :userId1 AND m.recipient.id = :userId2) OR " +
           "(m.sender.id = :userId2 AND m.recipient.id = :userId1) " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findDialog(@Param("userId1") Long userId1, @Param("userId2") Long userId2, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.recipient.id = :recipientId AND m.sender.id = :senderId AND m.read = false")
    void markAsRead(@Param("recipientId") Long recipientId, @Param("senderId") Long senderId);
}
