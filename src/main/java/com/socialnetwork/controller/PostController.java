package com.socialnetwork.controller;

import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.service.PostService;
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
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts")
public class PostController {

    private final PostService postService;
    private final UserService userService;

    @PostMapping("/wall")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create post on own wall")
    public PostResponse createPost(@AuthenticationPrincipal UserDetails userDetails,
                                   @Valid @RequestBody PostCreateRequest request) {
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        return postService.createWallPost(userId, request);
    }

    @GetMapping("/wall/{userId}")
    @Operation(summary = "Get wall posts of a user (paginated)")
    public Page<PostResponse> getWall(@PathVariable Long userId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return postService.getWallPosts(userId, page, size);
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Edit own post")
    public PostResponse updatePost(@AuthenticationPrincipal UserDetails userDetails,
                                   @PathVariable Long postId,
                                   @Valid @RequestBody PostCreateRequest request) {
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        return postService.updatePost(postId, userId, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own post")
    public void deletePost(@AuthenticationPrincipal UserDetails userDetails,
                           @PathVariable Long postId) {
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        postService.deletePost(postId, userId);
    }
}
