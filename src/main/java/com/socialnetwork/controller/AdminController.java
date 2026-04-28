package com.socialnetwork.controller;

import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated)")
    public Page<UserResponse> getUsers(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return adminService.getAllUsers(page, size);
    }

    @PostMapping("/users/{userId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ban a user")
    public void banUser(@PathVariable Long userId) {
        adminService.banUser(userId);
    }

    @PostMapping("/users/{userId}/unban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unban a user")
    public void unbanUser(@PathVariable Long userId) {
        adminService.unbanUser(userId);
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete any post")
    public void deletePost(@PathVariable Long postId) {
        adminService.deletePost(postId);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete any comment")
    public void deleteComment(@PathVariable Long commentId) {
        adminService.deleteComment(commentId);
    }
}
