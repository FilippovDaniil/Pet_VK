package com.socialnetwork.controller;

import com.socialnetwork.dto.request.CommentCreateRequest;
import com.socialnetwork.dto.response.CommentResponse;
import com.socialnetwork.service.CommentService;
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
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "Comments")
public class CommentController {

    private final CommentService commentService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add comment to a post")
    public CommentResponse addComment(@AuthenticationPrincipal UserDetails userDetails,
                                      @Valid @RequestBody CommentCreateRequest request) {
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        return commentService.addComment(userId, request);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get comments for a post (paginated)")
    public Page<CommentResponse> getComments(@PathVariable Long postId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return commentService.getComments(postId, page, size);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own comment")
    public void deleteComment(@AuthenticationPrincipal UserDetails userDetails,
                               @PathVariable Long commentId) {
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        commentService.deleteComment(commentId, userId);
    }
}
