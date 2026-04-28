package com.socialnetwork.controller;

import com.socialnetwork.dto.request.GroupCreateRequest;
import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.GroupResponse;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.service.GroupService;
import com.socialnetwork.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group")
    public GroupResponse createGroup(@AuthenticationPrincipal UserDetails userDetails,
                                     @Valid @RequestBody GroupCreateRequest request) {
        Long userId = getId(userDetails);
        return groupService.createGroup(userId, request);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group info")
    public GroupResponse getGroup(@PathVariable Long groupId) {
        return groupService.getGroup(groupId);
    }

    @PostMapping("/{groupId}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Join a group")
    public void join(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long groupId) {
        groupService.joinGroup(getId(userDetails), groupId);
    }

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave a group")
    public void leave(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long groupId) {
        groupService.leaveGroup(getId(userDetails), groupId);
    }

    @PostMapping("/{groupId}/admins/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Promote user to group admin (current admin only)")
    public void addAdmin(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long groupId,
                         @PathVariable Long userId) {
        groupService.addGroupAdmin(getId(userDetails), groupId, userId);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete group (owner or super-admin)")
    public void deleteGroup(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long groupId) {
        groupService.deleteGroup(getId(userDetails), groupId);
    }

    @PostMapping("/{groupId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create post in group")
    public PostResponse createGroupPost(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long groupId,
                                        @Valid @RequestBody PostCreateRequest request) {
        return groupService.createGroupPost(getId(userDetails), groupId, request);
    }

    @GetMapping("/{groupId}/posts")
    @Operation(summary = "Get posts in group (paginated)")
    public Page<PostResponse> getGroupPosts(@PathVariable Long groupId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return groupService.getGroupPosts(groupId, page, size);
    }

    private Long getId(UserDetails userDetails) {
        return userService.getUserByEmail(userDetails.getUsername()).getId();
    }
}
