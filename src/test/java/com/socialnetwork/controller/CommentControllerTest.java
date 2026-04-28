package com.socialnetwork.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.dto.request.CommentCreateRequest;
import com.socialnetwork.dto.response.CommentResponse;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.service.CommentService;
import com.socialnetwork.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@ActiveProfiles("test")
class CommentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CommentService commentService;
    @MockBean UserService userService;

    // Security context dependencies required by SecurityConfig
    @MockBean com.socialnetwork.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.socialnetwork.security.OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean com.socialnetwork.security.CustomUserDetailsService customUserDetailsService;
    @MockBean com.socialnetwork.service.BlacklistService blacklistService;

    private User testUser;
    private CommentResponse sampleCommentResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@test.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.ROLE_USER)
                .banned(false)
                .build();

        sampleCommentResponse = CommentResponse.builder()
                .id(50L)
                .postId(10L)
                .authorId(1L)
                .authorName("Test User")
                .text("Nice post!")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/comments
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void addComment_returns201() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(10L);
        request.setText("Nice post!");

        when(userService.getUserByEmail("test@test.com")).thenReturn(testUser);
        when(commentService.addComment(eq(1L), any(CommentCreateRequest.class)))
                .thenReturn(sampleCommentResponse);

        mockMvc.perform(post("/api/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.postId").value(10))
                .andExpect(jsonPath("$.authorId").value(1))
                .andExpect(jsonPath("$.text").value("Nice post!"));

        verify(commentService).addComment(eq(1L), any(CommentCreateRequest.class));
    }

    @Test
    void addComment_unauthenticated_returns401() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(10L);
        request.setText("Nice post!");

        mockMvc.perform(post("/api/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commentService);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void addComment_missingPostId_returns400() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        // postId is null — @NotNull should reject
        request.setText("Nice post!");

        mockMvc.perform(post("/api/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void addComment_blankText_returns400() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(10L);
        request.setText(""); // blank — @NotBlank should reject

        mockMvc.perform(post("/api/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }

    // -------------------------------------------------------------------------
    // GET /api/comments/{postId}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void getComments_returns200() throws Exception {
        List<CommentResponse> comments = List.of(
                sampleCommentResponse,
                CommentResponse.builder()
                        .id(51L).postId(10L).authorId(2L)
                        .authorName("Other User").text("Agree!")
                        .createdAt(LocalDateTime.now())
                        .build()
        );
        Page<CommentResponse> page = new PageImpl<>(comments, PageRequest.of(0, 20), 2);

        when(commentService.getComments(eq(10L), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/comments/10")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[0].text").value("Nice post!"))
                .andExpect(jsonPath("$.content[1].id").value(51))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(commentService).getComments(10L, 0, 20);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void getComments_defaultPagination_uses20() throws Exception {
        Page<CommentResponse> emptyPage = new PageImpl<>(List.of());
        when(commentService.getComments(eq(10L), eq(0), eq(20))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/comments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Verify that default size=20 is applied (matching the controller's defaultValue)
        verify(commentService).getComments(10L, 0, 20);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void getComments_emptyResult_returns200WithEmptyList() throws Exception {
        Page<CommentResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(commentService.getComments(eq(99L), anyInt(), anyInt())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/comments/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/comments/{commentId}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void deleteComment_returns204() throws Exception {
        when(userService.getUserByEmail("test@test.com")).thenReturn(testUser);
        doNothing().when(commentService).deleteComment(50L, 1L);

        mockMvc.perform(delete("/api/comments/50")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentService).deleteComment(50L, 1L);
    }

    @Test
    void deleteComment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/comments/50")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commentService);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void deleteComment_delegatesCorrectUserIdFromPrincipal() throws Exception {
        // Verify that the controller resolves userId from the authenticated principal,
        // not from a request parameter — this is the core security invariant.
        User anotherUser = User.builder()
                .id(99L).email("test@test.com")
                .firstName("Test").lastName("User")
                .role(Role.ROLE_USER).banned(false).build();

        when(userService.getUserByEmail("test@test.com")).thenReturn(anotherUser);
        doNothing().when(commentService).deleteComment(50L, 99L);

        mockMvc.perform(delete("/api/comments/50")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentService).deleteComment(50L, 99L);
    }
}
