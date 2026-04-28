package com.socialnetwork.service;

import com.socialnetwork.dto.response.FriendRequestResponse;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.FriendRequest;
import com.socialnetwork.entity.FriendRequestStatus;
import com.socialnetwork.entity.User;
import com.socialnetwork.event.FriendEvent;
import com.socialnetwork.event.FriendEventPublisher;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserService userService;
    private final FriendEventPublisher eventPublisher;

    @Transactional
    public FriendRequestResponse sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new BadRequestException("Cannot send friend request to yourself");
        }
        User requester = userService.getUserById(requesterId);
        User addressee = userService.getUserById(addresseeId);

        friendRequestRepository.findBetweenUsers(requesterId, addresseeId).ifPresent(fr -> {
            throw new BadRequestException("Friend request already exists");
        });

        FriendRequest fr = FriendRequest.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendRequestStatus.PENDING)
                .build();
        fr = friendRequestRepository.save(fr);
        eventPublisher.publish(FriendEvent.of("FRIEND_REQUEST_SENT", requesterId, addresseeId));
        return FriendRequestResponse.from(fr);
    }

    public List<FriendRequestResponse> getIncomingRequests(Long userId) {
        User user = userService.getUserById(userId);
        return friendRequestRepository.findByAddresseeAndStatus(user, FriendRequestStatus.PENDING)
                .stream().map(FriendRequestResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public FriendRequestResponse respondToRequest(Long requestId, String action, Long currentUserId) {
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("FriendRequest", requestId));
        if (!fr.getAddressee().getId().equals(currentUserId)) {
            throw new ForbiddenException("Not allowed to respond to this request");
        }
        if (fr.getStatus() != FriendRequestStatus.PENDING) {
            throw new BadRequestException("Request already processed");
        }
        if ("accept".equalsIgnoreCase(action)) {
            fr.setStatus(FriendRequestStatus.ACCEPTED);
            eventPublisher.publish(FriendEvent.of("FRIEND_REQUEST_ACCEPTED", fr.getRequester().getId(), currentUserId));
        } else if ("reject".equalsIgnoreCase(action)) {
            fr.setStatus(FriendRequestStatus.DECLINED);
        } else {
            throw new BadRequestException("Unknown action: " + action);
        }
        return FriendRequestResponse.from(friendRequestRepository.save(fr));
    }

    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        FriendRequest fr = friendRequestRepository.findBetweenUsers(userId, friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));
        if (fr.getStatus() != FriendRequestStatus.ACCEPTED) {
            throw new BadRequestException("Users are not friends");
        }
        friendRequestRepository.delete(fr);
    }

    public List<UserResponse> getFriends(Long userId) {
        return friendRequestRepository.findFriendsByUserId(userId).stream()
                .map(fr -> {
                    User friend = fr.getRequester().getId().equals(userId) ? fr.getAddressee() : fr.getRequester();
                    return UserResponse.from(friend);
                })
                .collect(Collectors.toList());
    }
}
