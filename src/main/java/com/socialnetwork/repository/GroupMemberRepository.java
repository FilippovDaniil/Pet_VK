package com.socialnetwork.repository;

import com.socialnetwork.entity.GroupMember;
import com.socialnetwork.entity.GroupMemberId;
import com.socialnetwork.entity.GroupMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserIdAndRole(Long groupId, Long userId, GroupMemberRole role);

    void deleteByGroupIdAndUserId(Long groupId, Long userId);
}
