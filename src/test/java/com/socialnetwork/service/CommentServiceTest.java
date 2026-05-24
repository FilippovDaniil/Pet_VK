package com.socialnetwork.service;

import com.socialnetwork.dto.request.CommentCreateRequest;
import com.socialnetwork.dto.response.CommentResponse;
import com.socialnetwork.entity.Comment;
import com.socialnetwork.entity.Post;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock PostService postService;
    @Mock UserService userService;

    @InjectMocks CommentService commentService;

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

    private User createBannedUser(Long id) {
        return User.builder()
                .id(id)
                .email("banned" + id + "@test.com")
                .firstName("Banned")
                .lastName("User")
                .role(Role.ROLE_USER)
                .banned(true)
                .build();
    }

    private Post createPost(Long id, User author) {
        return Post.builder()
                .id(id)
                .author(author)
                .text("Post text " + id)
                .build();
    }

    private Comment createComment(Long id, User author, Post post) {
        return Comment.builder()
                .id(id)
                .author(author)
                .post(post)
                .text("Comment text " + id)
                .build();
    }

    private CommentCreateRequest createRequest(String text) {
        CommentCreateRequest req = new CommentCreateRequest();
        req.setText(text);
        return req;
    }

    // -------------------------------------------------------------------------
    // addComment
    // -------------------------------------------------------------------------

    @Test
    void addComment_success() {
        User author = createUser(1L, Role.ROLE_USER);
        Post post = createPost(10L, createUser(2L, Role.ROLE_USER));

        when(userService.getUserById(1L)).thenReturn(author);
        when(postService.getPostById(10L)).thenReturn(post);

        Comment savedComment = createComment(99L, author, post);
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        CommentResponse response = commentService.addComment(1L, 10L, createRequest("Great post!"));

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getAuthorId()).isEqualTo(1L);
        assertThat(response.getPostId()).isEqualTo(10L);
        assertThat(response.getText()).isEqualTo("Comment text 99");

        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void addComment_bannedUser_throws() {
        User bannedUser = createBannedUser(5L);

        when(userService.getUserById(5L)).thenReturn(bannedUser);

        assertThatThrownBy(() -> commentService.addComment(5L, 10L, createRequest("Trying to comment")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Banned");

        verify(commentRepository, never()).save(any());
        verifyNoInteractions(postService);
    }

    // -------------------------------------------------------------------------
    // getComments
    // -------------------------------------------------------------------------

    @Test
    void getComments_returnsPage() {
        User author = createUser(1L, Role.ROLE_USER);
        Post post = createPost(10L, createUser(2L, Role.ROLE_USER));

        List<Comment> comments = List.of(
                createComment(1L, author, post),
                createComment(2L, author, post),
                createComment(3L, author, post)
        );
        Page<Comment> commentPage = new PageImpl<>(comments, PageRequest.of(0, 20), 3);
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(eq(10L), any()))
                .thenReturn(commentPage);

        Page<CommentResponse> result = commentService.getComments(10L, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(CommentResponse::getPostId)
                .containsOnly(10L);
        assertThat(result.getContent()).extracting(CommentResponse::getAuthorId)
                .containsOnly(1L);
    }

    @Test
    void getComments_emptyPost_returnsEmptyPage() {
        Page<Comment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(eq(99L), any()))
                .thenReturn(emptyPage);

        Page<CommentResponse> result = commentService.getComments(99L, 0, 20);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // deleteComment
    // -------------------------------------------------------------------------

    @Test
    void deleteComment_byOwner_success() {
        User author = createUser(1L, Role.ROLE_USER);
        Post post = createPost(10L, createUser(2L, Role.ROLE_USER));
        Comment comment = createComment(50L, author, post);

        when(commentRepository.findById(50L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(50L, 1L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_notOwner_throws() {
        User author = createUser(1L, Role.ROLE_USER);
        Post post = createPost(10L, createUser(2L, Role.ROLE_USER));
        Comment comment = createComment(50L, author, post);

        when(commentRepository.findById(50L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(50L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Not your comment");

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_notFound_throws() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // deleteCommentByAdmin
    // -------------------------------------------------------------------------

    @Test
    void deleteCommentByAdmin_success() {
        User author = createUser(1L, Role.ROLE_USER);
        Post post = createPost(10L, createUser(2L, Role.ROLE_USER));
        Comment comment = createComment(55L, author, post);

        when(commentRepository.findById(55L)).thenReturn(Optional.of(comment));

        commentService.deleteCommentByAdmin(55L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteCommentByAdmin_notFound_throws() {
        when(commentRepository.findById(888L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteCommentByAdmin(888L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentRepository, never()).delete(any());
    }
}
