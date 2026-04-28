package com.socialnetwork.controller;

import com.socialnetwork.dto.request.UpdateProfileRequest;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public UserResponse getMe(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return UserResponse.from(user);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile")
    public UserResponse updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return userService.updateProfile(user.getId(), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar image")
    public UserResponse uploadAvatar(@AuthenticationPrincipal UserDetails userDetails,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return userService.uploadAvatar(user.getId(), file);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by name or email")
    public Page<UserResponse> search(@RequestParam String query,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return userService.searchUsers(query, page, size);
    }
}
