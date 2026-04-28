package com.socialnetwork.service;

import com.socialnetwork.dto.request.CommentCreateRequest;
import com.socialnetwork.dto.response.CommentResponse;
import com.socialnetwork.entity.Comment;
import com.socialnetwork.entity.Post;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostService postService;
    private final UserService userService;

    @Transactional
    public CommentResponse addComment(Long userId, CommentCreateRequest request) {
        User author = userService.getUserById(userId);
        if (author.isBanned()) throw new ForbiddenException("Banned users cannot comment");
        Post post = postService.getPostById(request.getPostId());
        Comment comment = Comment.builder()
                .author(author)
                .post(post)
                .text(request.getText())
                .build();
        return CommentResponse.from(commentRepository.save(comment));
    }

    public Page<CommentResponse> getComments(Long postId, int page, int size) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId, PageRequest.of(page, size))
                .map(CommentResponse::from);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = getCommentById(commentId);
        if (!comment.getAuthor().getId().equals(userId)) throw new ForbiddenException("Not your comment");
        commentRepository.delete(comment);
    }

    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        commentRepository.delete(getCommentById(commentId));
    }

    private Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    }
}
