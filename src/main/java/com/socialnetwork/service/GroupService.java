package com.socialnetwork.service;

import com.socialnetwork.dto.request.GroupCreateRequest;
import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.GroupResponse;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.entity.*;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.GroupMemberRepository;
import com.socialnetwork.repository.GroupRepository;
import com.socialnetwork.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PostRepository postRepository;
    private final UserService userService;

    @Transactional
    public GroupResponse createGroup(Long userId, GroupCreateRequest request) {
        User owner = userService.getUserById(userId);
        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .owner(owner)
                .build();
        group = groupRepository.save(group);

        GroupMember member = GroupMember.builder()
                .id(new GroupMemberId(group.getId(), userId))
                .group(group)
                .user(owner)
                .role(GroupMemberRole.ADMIN)
                .build();
        groupMemberRepository.save(member);
        return GroupResponse.from(group);
    }

    public GroupResponse getGroup(Long groupId) {
        return GroupResponse.from(findGroupById(groupId));
    }

    @Transactional
    public void joinGroup(Long userId, Long groupId) {
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("Already a member");
        }
        Group group = findGroupById(groupId);
        User user = userService.getUserById(userId);
        GroupMember member = GroupMember.builder()
                .id(new GroupMemberId(groupId, userId))
                .group(group)
                .user(user)
                .role(GroupMemberRole.MEMBER)
                .build();
        groupMemberRepository.save(member);
    }

    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("Not a member");
        }
        Group group = findGroupById(groupId);
        if (group.getOwner().getId().equals(userId)) {
            throw new BadRequestException("Owner cannot leave the group");
        }
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    @Transactional
    public void addGroupAdmin(Long requesterId, Long groupId, Long targetUserId) {
        requireGroupAdmin(requesterId, groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this group"));
        member.setRole(GroupMemberRole.ADMIN);
        groupMemberRepository.save(member);
    }

    @Transactional
    public void deleteGroup(Long requesterId, Long groupId) {
        Group group = findGroupById(groupId);
        User requester = userService.getUserById(requesterId);
        boolean isOwner = group.getOwner().getId().equals(requesterId);
        boolean isSuperAdmin = requester.getRole() == Role.ROLE_ADMIN;
        if (!isOwner && !isSuperAdmin) throw new ForbiddenException("Only owner or admin can delete the group");
        groupRepository.delete(group);
    }

    @Transactional
    public PostResponse createGroupPost(Long userId, Long groupId, PostCreateRequest request) {
        requireGroupMember(userId, groupId);
        User author = userService.getUserById(userId);
        if (author.isBanned()) throw new ForbiddenException("Banned users cannot post");
        Group group = findGroupById(groupId);
        com.socialnetwork.entity.Post post = com.socialnetwork.entity.Post.builder()
                .author(author)
                .group(group)
                .text(request.getText())
                .imageUrl(request.getImageUrl())
                .build();
        return PostResponse.from(postRepository.save(post));
    }

    public Page<PostResponse> getGroupPosts(Long groupId, int page, int size) {
        return postRepository.findByGroupIdOrderByCreatedAtDesc(groupId, PageRequest.of(page, size))
                .map(PostResponse::from);
    }

    private Group findGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
    }

    private void requireGroupAdmin(Long userId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, userId, GroupMemberRole.ADMIN)) {
            throw new ForbiddenException("Group admin rights required");
        }
    }

    private void requireGroupMember(Long userId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("Must be a group member to post");
        }
    }
}
