package com.socialnetwork.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.dto.request.LoginRequest;
import com.socialnetwork.dto.request.RegisterRequest;
import com.socialnetwork.dto.response.AuthResponse;
import com.socialnetwork.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты HTTP-слоя для {@link AuthController}.
 *
 * <p>{@code @WebMvcTest} поднимает только MVC-контекст (фильтры, диспетчер, контроллер) —
 * без полного Spring-контекста и без базы данных. Это быстрее, чем {@code @SpringBootTest}.
 *
 * <p>{@code @ActiveProfiles("test")} активирует {@code application-test.yml}, в котором
 * обычно задаются in-memory настройки.
 *
 * <p>Все бины из SecurityConfig, которые требуют реальных зависимостей (Redis, JWT),
 * заменяются {@code @MockBean} — иначе контекст не поднимется.
 *
 * <p>Покрываемые сценарии:
 * <ul>
 *   <li>Регистрация с корректными данными → HTTP 201 + JWT в ответе</li>
 *   <li>Вход с корректными данными → HTTP 200 + JWT в ответе</li>
 *   <li>Регистрация с невалидным email → HTTP 400 (Bean Validation)</li>
 * </ul>
 */
@WebMvcTest(controllers = AuthController.class)
@ActiveProfiles("test")
class AuthControllerTest {

    /** MockMvc — инструмент для отправки HTTP-запросов без реального сервера. */
    @Autowired MockMvc mockMvc;
    /** Jackson ObjectMapper для сериализации DTO в JSON-строку запроса. */
    @Autowired ObjectMapper objectMapper;

    /** Mock сервиса аутентификации — контроллер делегирует всю логику ему. */
    @MockBean AuthService authService;

    // SecurityConfig требует этих бинов в контексте — без mock'ов контекст не запустится
    @MockBean com.socialnetwork.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.socialnetwork.security.OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean com.socialnetwork.security.CustomUserDetailsService customUserDetailsService;
    @MockBean com.socialnetwork.service.BlacklistService blacklistService;

    @Test
    void register_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("user@example.com");
        req.setPassword("password123");
        req.setFirstName("Alice");
        req.setLastName("Smith");

        AuthResponse resp = AuthResponse.builder()
                .accessToken("jwt")
                .refreshToken("1:refresh")
                .tokenType("Bearer")
                .build();
        when(authService.register(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt"));
    }

    @Test
    void login_returns200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("password123");

        AuthResponse resp = AuthResponse.builder()
                .accessToken("jwt-token")
                .refreshToken("1:refresh")
                .tokenType("Bearer")
                .build();
        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("not-an-email");
        req.setPassword("password123");
        req.setFirstName("Alice");
        req.setLastName("Smith");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
