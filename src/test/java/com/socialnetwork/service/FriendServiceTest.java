package com.socialnetwork.service;

import com.socialnetwork.dto.response.FriendRequestResponse;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.FriendRequest;
import com.socialnetwork.entity.FriendRequestStatus;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.event.FriendEvent;
import com.socialnetwork.event.FriendEventPublisher;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.FriendRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock FriendRequestRepository friendRequestRepository;
    @Mock UserService userService;
    @Mock FriendEventPublisher eventPublisher;

    @InjectMocks FriendService friendService;

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

    private FriendRequest pendingRequest(Long id, User requester, User addressee) {
        return FriendRequest.builder()
                .id(id)
                .requester(requester)
                .addressee(addressee)
                .status(FriendRequestStatus.PENDING)
                .build();
    }

    private FriendRequest acceptedRequest(Long id, User requester, User addressee) {
        return FriendRequest.builder()
                .id(id)
                .requester(requester)
                .addressee(addressee)
                .status(FriendRequestStatus.ACCEPTED)
                .build();
    }

    // -------------------------------------------------------------------------
    // sendRequest
    // -------------------------------------------------------------------------

    @Test
    void sendRequest_success() {
        User requester = createUser(1L, Role.ROLE_USER);
        User addressee = createUser(2L, Role.ROLE_USER);

        when(userService.getUserById(1L)).thenReturn(requester);
        when(userService.getUserById(2L)).thenReturn(addressee);
        when(friendRequestRepository.findBetweenUsers(1L, 2L)).thenReturn(Optional.empty());

        FriendRequest saved = pendingRequest(10L, requester, addressee);
        when(friendRequestRepository.save(any(FriendRequest.class))).thenReturn(saved);

        FriendRequestResponse response = friendService.sendRequest(1L, 2L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRequesterId()).isEqualTo(1L);
        assertThat(response.getAddresseeId()).isEqualTo(2L);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        // Verify Kafka event was published with correct type and participants
        ArgumentCaptor<FriendEvent> eventCaptor = ArgumentCaptor.forClass(FriendEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        FriendEvent published = eventCaptor.getValue();
        assertThat(published.getType()).isEqualTo("FRIEND_REQUEST_SENT");
        assertThat(published.getSourceUserId()).isEqualTo(1L);
        assertThat(published.getTargetUserId()).isEqualTo(2L);
    }

    @Test
    void sendRequest_toSelf_throws() {
        assertThatThrownBy(() -> friendService.sendRequest(1L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yourself");

        verifyNoInteractions(userService, friendRequestRepository, eventPublisher);
    }

    @Test
    void sendRequest_alreadyExists_throws() {
        User requester = createUser(1L, Role.ROLE_USER);
        User addressee = createUser(2L, Role.ROLE_USER);

        when(userService.getUserById(1L)).thenReturn(requester);
        when(userService.getUserById(2L)).thenReturn(addressee);

        FriendRequest existing = pendingRequest(5L, requester, addressee);
        when(friendRequestRepository.findBetweenUsers(1L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendService.sendRequest(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(friendRequestRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // -------------------------------------------------------------------------
    // getIncomingRequests
    // -------------------------------------------------------------------------

    @Test
    void getIncomingRequests_returnsPendingOnly() {
        User addressee = createUser(2L, Role.ROLE_USER);
        User requester1 = createUser(10L, Role.ROLE_USER);
        User requester2 = createUser(11L, Role.ROLE_USER);

        when(userService.getUserById(2L)).thenReturn(addressee);

        List<FriendRequest> pending = List.of(
                pendingRequest(1L, requester1, addressee),
                pendingRequest(2L, requester2, addressee)
        );
        when(friendRequestRepository.findByAddresseeAndStatus(addressee, FriendRequestStatus.PENDING))
                .thenReturn(pending);

        List<FriendRequestResponse> result = friendService.getIncomingRequests(2L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FriendRequestResponse::getStatus)
                .containsOnly("PENDING");
        assertThat(result).extracting(FriendRequestResponse::getAddresseeId)
                .containsOnly(2L);
    }

    // -------------------------------------------------------------------------
    // respondToRequest
    // -------------------------------------------------------------------------

    @Test
    void respondToRequest_accept_setsAccepted() {
        User requester = createUser(1L, Role.ROLE_USER);
        User addressee = createUser(2L, Role.ROLE_USER);
        FriendRequest fr = pendingRequest(7L, requester, addressee);

        when(friendRequestRepository.findById(7L)).thenReturn(Optional.of(fr));
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        FriendRequestResponse response = friendService.respondToRequest(7L, "accept", 2L);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");

        ArgumentCaptor<FriendEvent> eventCaptor = ArgumentCaptor.forClass(FriendEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("FRIEND_REQUEST_ACCEPTED");
        assertThat(eventCaptor.getValue().getSourceUserId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getTargetUserId()).isEqualTo(2L);
    }

    @Test
    void respondToRequest_reject_setsDeclined() {
        User requester = createUser(1L, Role.ROLE_USER);
        User addressee = createUser(2L, Role.ROLE_USER);
        FriendRequest fr = pendingRequest(8L, requester, addressee);

        when(friendRequestRepository.findById(8L)).thenReturn(Optional.of(fr));
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        FriendRequestResponse response = friendService.respondToRequest(8L, "reject", 2L);

        assertThat(response.getStatus()).isEqualTo("DECLINED");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void respondToRequest_notAddressee_throws() {
        User requester = createUser(1L, Role.ROLE_USER);
        User addressee = createUser(2L, Role.ROLE_USER);
        FriendRequest fr = pendingRequest(9L, requester, addressee);

        when(friendRequestRepository.findById(9L)).thenReturn(Optional.of(fr));

        // currentUserId = 3L is neither requester nor addressee
        assertThatThrownBy(() -> friendService.respondToRequest(9L, "accept", 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Not allowed");

        verify(friendRequestRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void respondToRequest_alreadyProcessed_throws() {
        User requester = createUser(1L, Role.ROLE_USER);
        User addressee = createUser(2L, Role.ROLE_USER);
        FriendRequest fr = acceptedRequest(11L, requester, addressee);

        when(friendRequestRepository.findById(11L)).thenReturn(Optional.of(fr));

        assertThatThrownBy(() -> friendService.respondToRequest(11L, "accept", 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already processed");

        verify(friendRequestRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // removeFriend
    // -------------------------------------------------------------------------

    @Test
    void removeFriend_success() {
        User u1 = createUser(1L, Role.ROLE_USER);
        User u2 = createUser(2L, Role.ROLE_USER);
        FriendRequest accepted = acceptedRequest(20L, u1, u2);

        when(friendRequestRepository.findBetweenUsers(1L, 2L)).thenReturn(Optional.of(accepted));

        friendService.removeFriend(1L, 2L);

        verify(friendRequestRepository).delete(accepted);
    }

    @Test
    void removeFriend_notFriends_throws() {
        User u1 = createUser(1L, Role.ROLE_USER);
        User u2 = createUser(2L, Role.ROLE_USER);
        // The request exists but is still PENDING (not ACCEPTED)
        FriendRequest pending = pendingRequest(21L, u1, u2);

        when(friendRequestRepository.findBetweenUsers(1L, 2L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> friendService.removeFriend(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not friends");

        verify(friendRequestRepository, never()).delete(any());
    }

    @Test
    void removeFriend_noRelationship_throws() {
        when(friendRequestRepository.findBetweenUsers(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.removeFriend(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getFriends
    // -------------------------------------------------------------------------

    @Test
    void getFriends_returnsCorrectUsers() {
        User currentUser = createUser(1L, Role.ROLE_USER);
        User friendA = createUser(2L, Role.ROLE_USER);
        User friendB = createUser(3L, Role.ROLE_USER);

        // currentUser is the requester in one friendship and addressee in another
        FriendRequest fr1 = acceptedRequest(30L, currentUser, friendA);
        FriendRequest fr2 = acceptedRequest(31L, friendB, currentUser);

        when(friendRequestRepository.findFriendsByUserId(1L)).thenReturn(List.of(fr1, fr2));

        List<UserResponse> friends = friendService.getFriends(1L);

        assertThat(friends).hasSize(2);
        assertThat(friends).extracting(UserResponse::getId)
                .containsExactlyInAnyOrder(2L, 3L);
    }
}
