package com.socialnetwork.controller;

import com.socialnetwork.dto.response.FriendRequestResponse;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.service.FriendService;
import com.socialnetwork.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Tag(name = "Friends")
public class FriendController {

    private final FriendService friendService;
    private final UserService userService;

    @PostMapping("/requests/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send friend request")
    public FriendRequestResponse sendRequest(@AuthenticationPrincipal UserDetails userDetails,
                                              @PathVariable Long userId) {
        Long currentId = getCurrentUserId(userDetails);
        return friendService.sendRequest(currentId, userId);
    }

    @GetMapping("/requests/incoming")
    @Operation(summary = "Get incoming friend requests")
    public List<FriendRequestResponse> getIncoming(@AuthenticationPrincipal UserDetails userDetails) {
        return friendService.getIncomingRequests(getCurrentUserId(userDetails));
    }

    @PutMapping("/requests/{requestId}")
    @Operation(summary = "Accept or reject friend request (action=accept|reject)")
    public FriendRequestResponse respond(@AuthenticationPrincipal UserDetails userDetails,
                                          @PathVariable Long requestId,
                                          @RequestParam String action) {
        return friendService.respondToRequest(requestId, action, getCurrentUserId(userDetails));
    }

    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove friend")
    public void removeFriend(@AuthenticationPrincipal UserDetails userDetails,
                             @PathVariable Long friendId) {
        friendService.removeFriend(getCurrentUserId(userDetails), friendId);
    }

    @GetMapping
    @Operation(summary = "Get friend list")
    public List<UserResponse> getFriends(@AuthenticationPrincipal UserDetails userDetails) {
        return friendService.getFriends(getCurrentUserId(userDetails));
    }

    private Long getCurrentUserId(UserDetails userDetails) {
        return userService.getUserByEmail(userDetails.getUsername()).getId();
    }
}
