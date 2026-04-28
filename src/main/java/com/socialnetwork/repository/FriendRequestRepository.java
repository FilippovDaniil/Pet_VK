package com.socialnetwork.repository;

import com.socialnetwork.entity.FriendRequest;
import com.socialnetwork.entity.FriendRequestStatus;
import com.socialnetwork.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findByRequesterAndAddressee(User requester, User addressee);

    List<FriendRequest> findByAddresseeAndStatus(User addressee, FriendRequestStatus status);

    @Query("SELECT fr FROM FriendRequest fr WHERE " +
           "(fr.requester.id = :userId OR fr.addressee.id = :userId) " +
           "AND fr.status = 'ACCEPTED'")
    List<FriendRequest> findFriendsByUserId(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRequest fr WHERE " +
           "((fr.requester.id = :userId1 AND fr.addressee.id = :userId2) OR " +
           "(fr.requester.id = :userId2 AND fr.addressee.id = :userId1)) " +
           "AND fr.status = 'ACCEPTED'")
    boolean areFriends(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query("SELECT fr FROM FriendRequest fr WHERE " +
           "(fr.requester.id = :userId1 AND fr.addressee.id = :userId2) OR " +
           "(fr.requester.id = :userId2 AND fr.addressee.id = :userId1)")
    Optional<FriendRequest> findBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
