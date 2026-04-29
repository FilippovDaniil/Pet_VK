package com.socialnetwork.controller;

import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер административных функций платформы.
 *
 * <p>Предоставляет эндпоинты для управления пользователями и модерации контента.
 * Доступ строго ограничен: только пользователи с ролью {@code ROLE_ADMIN}
 * могут вызывать методы этого контроллера.
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — REST-контроллер, возвращает JSON.</li>
 *   <li>{@code @RequestMapping("/api/admin")} — базовый URL всех административных эндпоинтов.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok создаёт конструктор для {@code adminService}.</li>
 *   <li>{@code @PreAuthorize("hasRole('ADMIN')")} — проверка роли на уровне класса:
 *       Spring Security проверяет роль перед вызовом ЛЮБОГО метода этого контроллера.
 *       Это удобнее, чем дублировать аннотацию на каждом методе.</li>
 *   <li>{@code @Tag(name = "Admin")} — группировка в Swagger UI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
// Глобальная проверка роли: если токен не содержит ROLE_ADMIN — вернётся 403 Forbidden
// ещё до вызова метода сервиса. Требует @EnableMethodSecurity в SecurityConfig.
@Tag(name = "Admin")
public class AdminController {

    // Сервис административной бизнес-логики: бан, разбан, удаление контента
    private final AdminService adminService;

    /**
     * Постраничный список всех пользователей платформы.
     *
     * <p>Позволяет администратору просматривать все аккаунты, их статус и роли.
     *
     * @param page номер страницы (0-based), по умолчанию 0
     * @param size количество пользователей на странице, по умолчанию 20
     * @return {@link Page} с {@link UserResponse} — все пользователи платформы
     */
    @GetMapping("/users")
    @Operation(summary = "List all users (paginated)")
    public Page<UserResponse> getUsers(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return adminService.getAllUsers(page, size);
    }

    /**
     * Блокирует учётную запись пользователя.
     *
     * <p>Заблокированный пользователь не может войти в систему и публиковать контент.
     *
     * @param userId ID блокируемого пользователя (из пути URL)
     *
     * <p>{@code @PostMapping("/users/{userId}/ban")} — HTTP POST /api/admin/users/{userId}/ban.
     * Возвращает 204 No Content при успехе.
     */
    @PostMapping("/users/{userId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ban a user")
    public void banUser(@PathVariable Long userId) {
        adminService.banUser(userId);
    }

    /**
     * Снимает блокировку с учётной записи пользователя.
     *
     * <p>После разблокировки пользователь может снова войти в систему.
     *
     * @param userId ID разблокируемого пользователя
     *
     * <p>{@code @PostMapping("/users/{userId}/unban")} — HTTP POST /api/admin/users/{userId}/unban.
     */
    @PostMapping("/users/{userId}/unban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unban a user")
    public void unbanUser(@PathVariable Long userId) {
        adminService.unbanUser(userId);
    }

    /**
     * Удаляет любой пост платформы (модерация контента).
     *
     * <p>Администратор может удалить пост любого пользователя без проверки авторства.
     *
     * @param postId ID удаляемого поста
     *
     * <p>{@code @DeleteMapping("/posts/{postId}")} — HTTP DELETE /api/admin/posts/{postId}.
     */
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete any post")
    public void deletePost(@PathVariable Long postId) {
        adminService.deletePost(postId);
    }

    /**
     * Удаляет любой комментарий платформы (модерация контента).
     *
     * <p>Администратор может удалить комментарий любого пользователя.
     *
     * @param commentId ID удаляемого комментария
     *
     * <p>{@code @DeleteMapping("/comments/{commentId}")} — HTTP DELETE /api/admin/comments/{commentId}.
     */
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete any comment")
    public void deleteComment(@PathVariable Long commentId) {
        adminService.deleteComment(commentId);
    }
}
