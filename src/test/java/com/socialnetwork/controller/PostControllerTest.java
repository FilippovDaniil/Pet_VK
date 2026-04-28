package com.socialnetwork.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.service.PostService;
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

@WebMvcTest(PostController.class)
@ActiveProfiles("test")
class PostControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PostService postService;
    @MockBean UserService userService;

    // Security context dependencies required by SecurityConfig
    @MockBean com.socialnetwork.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.socialnetwork.security.OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean com.socialnetwork.security.CustomUserDetailsService customUserDetailsService;
    @MockBean com.socialnetwork.service.BlacklistService blacklistService;

    private User testUser;
    private PostResponse samplePostResponse;

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

        samplePostResponse = PostResponse.builder()
                .id(100L)
                .authorId(1L)
                .authorName("Test User")
                .text("Hello World")
                .imageUrl(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/posts/wall
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void createPost_authenticated_returns201() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setText("Hello World");

        when(userService.getUserByEmail("test@test.com")).thenReturn(testUser);
        when(postService.createWallPost(eq(1L), any(PostCreateRequest.class)))
                .thenReturn(samplePostResponse);

        mockMvc.perform(post("/api/posts/wall")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.text").value("Hello World"))
                .andExpect(jsonPath("$.authorId").value(1));

        verify(postService).createWallPost(eq(1L), any(PostCreateRequest.class));
    }

    @Test
    void createPost_unauthenticated_returns401() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setText("Hello World");

        mockMvc.perform(post("/api/posts/wall")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void createPost_blankText_returns400() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setText("   "); // blank but not null — @NotBlank should reject

        mockMvc.perform(post("/api/posts/wall")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }

    // -------------------------------------------------------------------------
    // GET /api/posts/wall/{userId}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void getWall_returnsPage() throws Exception {
        List<PostResponse> posts = List.of(samplePostResponse);
        Page<PostResponse> page = new PageImpl<>(posts, PageRequest.of(0, 10), 1);

        when(postService.getWallPosts(eq(1L), eq(0), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/posts/wall/1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].text").value("Hello World"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(postService).getWallPosts(1L, 0, 10);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void getWall_defaultPagination() throws Exception {
        Page<PostResponse> emptyPage = new PageImpl<>(List.of());
        when(postService.getWallPosts(eq(2L), eq(0), eq(10))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/posts/wall/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // -------------------------------------------------------------------------
    // PUT /api/posts/{postId}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void updatePost_returns200() throws Exception {
        PostCreateRequest updateRequest = new PostCreateRequest();
        updateRequest.setText("Updated text");

        PostResponse updatedResponse = PostResponse.builder()
                .id(100L)
                .authorId(1L)
                .authorName("Test User")
                .text("Updated text")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.getUserByEmail("test@test.com")).thenReturn(testUser);
        when(postService.updatePost(eq(100L), eq(1L), any(PostCreateRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/posts/100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.text").value("Updated text"));

        verify(postService).updatePost(eq(100L), eq(1L), any(PostCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void updatePost_blankText_returns400() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setText("");

        mockMvc.perform(put("/api/posts/100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/posts/{postId}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "test@test.com", roles = {"USER"})
    void deletePost_returns204() throws Exception {
        when(userService.getUserByEmail("test@test.com")).thenReturn(testUser);
        doNothing().when(postService).deletePost(100L, 1L);

        mockMvc.perform(delete("/api/posts/100")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(100L, 1L);
    }

    @Test
    void deletePost_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/posts/100")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService);
    }
}
