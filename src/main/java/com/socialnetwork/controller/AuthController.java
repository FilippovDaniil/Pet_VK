package com.socialnetwork.controller;

import com.socialnetwork.dto.request.LoginRequest;
import com.socialnetwork.dto.request.RefreshTokenRequest;
import com.socialnetwork.dto.request.RegisterRequest;
import com.socialnetwork.dto.response.AuthResponse;
import com.socialnetwork.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер аутентификации.
 *
 * <p>Отвечает за регистрацию, вход, обновление токена и выход из системы.
 * Все эндпоинты данного контроллера публичны — они не требуют токена (кроме logout).
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — объединяет {@code @Controller} и {@code @ResponseBody}:
 *       каждый метод автоматически сериализует возвращаемый объект в JSON.</li>
 *   <li>{@code @RequestMapping("/api/auth")} — базовый URL-путь для всех методов контроллера.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok генерирует конструктор для всех {@code final}-полей,
 *       тем самым реализуя внедрение зависимостей через конструктор (рекомендованный Spring способ).</li>
 *   <li>{@code @Tag(name = "Authentication")} — метка для группировки в Swagger UI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    // Сервис бизнес-логики аутентификации: регистрация, вход, выдача токенов
    private final AuthService authService;

    /**
     * Регистрация нового пользователя.
     *
     * <p>Принимает данные новой учётной записи, создаёт пользователя в базе данных
     * и возвращает пару токенов (access + refresh).
     *
     * @param request тело запроса с email, паролем и именем — валидируется Spring Validation
     * @return {@link AuthResponse} с access-токеном, refresh-токеном и временем жизни
     *
     * <p>Аннотации метода:
     * <ul>
     *   <li>{@code @PostMapping("/register")} — обрабатывает HTTP POST на /api/auth/register.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.CREATED)} — устанавливает код ответа 201 Created
     *       вместо 200 OK, что семантически корректно при создании ресурса.</li>
     *   <li>{@code @Valid} — запускает Bean Validation на объекте {@code request};
     *       если нарушены ограничения (@NotBlank, @Email и т.д.) — выбрасывается
     *       {@code MethodArgumentNotValidException}, которую перехватывает GlobalExceptionHandler.</li>
     *   <li>{@code @RequestBody} — десериализует JSON-тело HTTP-запроса в Java-объект.</li>
     * </ul>
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        // Делегируем всю бизнес-логику сервисному слою
        return authService.register(request);
    }

    /**
     * Аутентификация пользователя по email и паролю.
     *
     * <p>Проверяет учётные данные, и при успехе возвращает новую пару токенов.
     *
     * @param request тело запроса с email и паролем
     * @return {@link AuthResponse} с токенами
     *
     * <p>{@code @PostMapping("/login")} — HTTP POST /api/auth/login.
     * Успешный ответ имеет статус 200 OK (по умолчанию).
     */
    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Обновление access-токена с помощью refresh-токена.
     *
     * <p>Клиент отправляет действующий refresh-токен и получает новый access-токен
     * (и, возможно, новый refresh-токен). Это позволяет не требовать повторный вход
     * каждый раз при истечении короткоживущего access-токена.
     *
     * @param request тело запроса с полем {@code refreshToken}
     * @return новый {@link AuthResponse}
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    /**
     * Выход из системы (инвалидация access-токена).
     *
     * <p>Извлекает JWT из заголовка Authorization и добавляет его в чёрный список,
     * чтобы он не мог быть повторно использован до истечения срока действия.
     *
     * @param authHeader значение заголовка Authorization (ожидается формат "Bearer &lt;token&gt;")
     *
     * <p>Аннотации метода:
     * <ul>
     *   <li>{@code @ResponseStatus(HttpStatus.NO_CONTENT)} — возвращает 204 No Content,
     *       т.е. тело ответа отсутствует, что семантически корректно для logout.</li>
     *   <li>{@code @RequestHeader("Authorization")} — извлекает значение HTTP-заголовка
     *       Authorization из входящего запроса.</li>
     * </ul>
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout (blacklist access token)")
    public void logout(@RequestHeader("Authorization") String authHeader) {
        // Убираем префикс "Bearer " если он присутствует, оставляя чистый токен
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        // Передаём токен в сервис для добавления в чёрный список
        authService.logout(token);
    }
}
