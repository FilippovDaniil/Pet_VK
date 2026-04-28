package com.socialnetwork.service;

import com.socialnetwork.dto.request.GroupCreateRequest;
import com.socialnetwork.dto.response.GroupResponse;
import com.socialnetwork.entity.*;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.GroupMemberRepository;
import com.socialnetwork.repository.GroupRepository;
import com.socialnetwork.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock PostRepository postRepository;
    @Mock UserService userService;

    @InjectMocks GroupService groupService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User createUser(Long id, Role role) {
        return User.builder()
                .id(id)
                .email("user" + id + "@test.com")
                .firstName("First" + id)
                .lastName("Last" + id)
                .role(role)
                .banned(false)
                .build();
    }

    private Group createGroup(Long id, User owner) {
        return Group.builder()
                .id(id)
                .name("Group " + id)
                .description("Description for group " + id)
                .owner(owner)
                .build();
    }

    private GroupCreateRequest createGroupRequest(String name) {
        GroupCreateRequest req = new GroupCreateRequest();
        req.setName(name);
        req.setDescription("Test description");
        req.setAvatarUrl(null);
        return req;
    }

    // -------------------------------------------------------------------------
    // createGroup
    // -------------------------------------------------------------------------

    @Test
    void createGroup_success() {
        User owner = createUser(1L, Role.ROLE_USER);
        GroupCreateRequest request = createGroupRequest("My Group");

        when(userService.getUserById(1L)).thenReturn(owner);

        Group savedGroup = createGroup(100L, owner);
        when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);

        GroupResponse response = groupService.createGroup(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getOwnerId()).isEqualTo(1L);

        // Verify the GroupMember with ADMIN role was also persisted
        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        GroupMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getRole()).isEqualTo(GroupMemberRole.ADMIN);
        assertThat(savedMember.getUser()).isEqualTo(owner);
        assertThat(savedMember.getGroup()).isEqualTo(savedGroup);
    }

    // -------------------------------------------------------------------------
    // joinGroup
    // -------------------------------------------------------------------------

    @Test
    void joinGroup_success() {
        User user = createUser(2L, Role.ROLE_USER);
        Group group = createGroup(100L, createUser(1L, Role.ROLE_USER));

        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(false);
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(userService.getUserById(2L)).thenReturn(user);

        groupService.joinGroup(2L, 100L);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        GroupMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(savedMember.getUser()).isEqualTo(user);
    }

    @Test
    void joinGroup_alreadyMember_throws() {
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.joinGroup(2L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Already a member");

        verify(groupMemberRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // leaveGroup
    // -------------------------------------------------------------------------

    @Test
    void leaveGroup_owner_throws() {
        User owner = createUser(1L, Role.ROLE_USER);
        Group group = createGroup(100L, owner);

        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 1L)).thenReturn(true);
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.leaveGroup(1L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Owner cannot leave");

        verify(groupMemberRepository, never()).deleteByGroupIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void leaveGroup_nonMember_throws() {
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> groupService.leaveGroup(5L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Not a member");

        verify(groupMemberRepository, never()).deleteByGroupIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void leaveGroup_regularMember_success() {
        User owner = createUser(1L, Role.ROLE_USER);
        User member = createUser(3L, Role.ROLE_USER);
        Group group = createGroup(100L, owner);

        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 3L)).thenReturn(true);
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        groupService.leaveGroup(3L, 100L);

        verify(groupMemberRepository).deleteByGroupIdAndUserId(100L, 3L);
    }

    // -------------------------------------------------------------------------
    // addGroupAdmin
    // -------------------------------------------------------------------------

    @Test
    void addGroupAdmin_notAdmin_throws() {
        // requester is not an ADMIN of the group
        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(100L, 2L, GroupMemberRole.ADMIN))
                .thenReturn(false);

        assertThatThrownBy(() -> groupService.addGroupAdmin(2L, 100L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Group admin rights required");

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void addGroupAdmin_byAdmin_success() {
        User target = createUser(3L, Role.ROLE_USER);
        GroupMember targetMember = GroupMember.builder()
                .id(new GroupMemberId(100L, 3L))
                .user(target)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(100L, 2L, GroupMemberRole.ADMIN))
                .thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 3L))
                .thenReturn(Optional.of(targetMember));

        groupService.addGroupAdmin(2L, 100L, 3L);

        assertThat(targetMember.getRole()).isEqualTo(GroupMemberRole.ADMIN);
        verify(groupMemberRepository).save(targetMember);
    }

    // -------------------------------------------------------------------------
    // deleteGroup
    // -------------------------------------------------------------------------

    @Test
    void deleteGroup_byOwner_success() {
        User owner = createUser(1L, Role.ROLE_USER);
        Group group = createGroup(100L, owner);

        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(userService.getUserById(1L)).thenReturn(owner);

        groupService.deleteGroup(1L, 100L);

        verify(groupRepository).delete(group);
    }

    @Test
    void deleteGroup_bySuperAdmin_success() {
        User owner = createUser(1L, Role.ROLE_USER);
        User superAdmin = createUser(99L, Role.ROLE_ADMIN);
        Group group = createGroup(100L, owner);

        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(userService.getUserById(99L)).thenReturn(superAdmin);

        // superAdmin is not the owner but has ROLE_ADMIN — should succeed
        groupService.deleteGroup(99L, 100L);

        verify(groupRepository).delete(group);
    }

    @Test
    void deleteGroup_byOther_throws() {
        User owner = createUser(1L, Role.ROLE_USER);
        User other = createUser(5L, Role.ROLE_USER);
        Group group = createGroup(100L, owner);

        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(userService.getUserById(5L)).thenReturn(other);

        assertThatThrownBy(() -> groupService.deleteGroup(5L, 100L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only owner or admin");

        verify(groupRepository, never()).delete(any());
    }

    @Test
    void deleteGroup_groupNotFound_throws() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.deleteGroup(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
