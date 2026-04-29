package com.socialnetwork.service;

import com.socialnetwork.dto.request.LoginRequest;
import com.socialnetwork.dto.request.RegisterRequest;
import com.socialnetwork.dto.response.AuthResponse;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.repository.UserRepository;
import com.socialnetwork.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link AuthService}.
 *
 * <p>Использует Mockito через расширение {@code @ExtendWith(MockitoExtension.class)}.
 * Все зависимости сервиса заменены mock-объектами — реальных баз данных и Redis нет.
 * Это позволяет тестировать только бизнес-логику AuthService в изоляции.
 *
 * <p>Покрываемые сценарии:
 * <ul>
 *   <li>Успешная регистрация нового пользователя</li>
 *   <li>Попытка регистрации с уже занятым email → BadRequestException</li>
 *   <li>Успешный вход по корректным учётным данным</li>
 *   <li>Вход с неверным паролем → BadCredentialsException</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** Mock репозитория пользователей — проверяем вызовы save/existsByEmail/findByEmail. */
    @Mock UserRepository userRepository;
    /** Mock BCrypt-кодировщика — возвращает фиктивный хеш, без реального вычисления. */
    @Mock PasswordEncoder passwordEncoder;
    /** Mock провайдера JWT — возвращает предсказуемые токены в тестах. */
    @Mock JwtTokenProvider tokenProvider;
    /** Mock сервиса refresh-токенов — изолирует Redis-зависимость. */
    @Mock RefreshTokenService refreshTokenService;
    /** Mock сервиса чёрного списка JWT — изолирует Redis-зависимость. */
    @Mock BlacklistService blacklistService;

    /**
     * Тестируемый объект. @InjectMocks создаёт экземпляр AuthService
     * и внедряет все поля, помеченные @Mock, через конструктор или setter.
     */
    @InjectMocks AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
    }

    @Test
    void register_success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        User savedUser = User.builder().id(1L).email("test@example.com")
                .firstName("John").lastName("Doe").role(Role.ROLE_USER).build();
        when(userRepository.save(any())).thenReturn(savedUser);
        when(tokenProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("1:refresh-token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("1:refresh-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void login_success() {
        User user = User.builder().id(1L).email("test@example.com")
                .password("encoded").firstName("John").lastName("Doe")
                .role(Role.ROLE_USER).banned(false).build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("1:refresh");

        LoginRequest login = new LoginRequest();
        login.setEmail("test@example.com");
        login.setPassword("password123");

        AuthResponse response = authService.login(login);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void login_wrongPassword_throws() {
        User user = User.builder().id(1L).email("test@example.com")
                .password("encoded").firstName("John").lastName("Doe")
                .role(Role.ROLE_USER).banned(false).build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        LoginRequest login = new LoginRequest();
        login.setEmail("test@example.com");
        login.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(login))
                .isInstanceOf(BadCredentialsException.class);
    }
}
