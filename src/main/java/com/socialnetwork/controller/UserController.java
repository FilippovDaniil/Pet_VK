package com.socialnetwork.controller;

import com.socialnetwork.dto.request.UpdateProfileRequest;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Контроллер управления профилями пользователей.
 *
 * <p>Предоставляет эндпоинты для просмотра и редактирования собственного профиля,
 * загрузки аватара и поиска других пользователей по имени или email.
 *
 * <p>Все эндпоинты требуют аутентификации (JWT в заголовке Authorization).
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — помечает класс как REST-контроллер Spring MVC;
 *       возвращаемые объекты автоматически сериализуются в JSON.</li>
 *   <li>{@code @RequestMapping("/api/users")} — базовый URL для всех методов.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok генерирует конструктор с аргументами
 *       для final-полей (userService), реализуя Constructor Injection.</li>
 *   <li>{@code @Tag(name = "Users")} — группировка эндпоинтов в Swagger UI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    // Сервис для работы с пользователями: поиск, обновление профиля, загрузка аватара
    private final UserService userService;

    /**
     * Получение профиля текущего аутентифицированного пользователя.
     *
     * <p>Использует данные из Security Context, чтобы найти пользователя в базе
     * по email (username) и вернуть его публичный профиль.
     *
     * @param userDetails детали текущего пользователя, автоматически внедряемые из Security Context
     * @return {@link UserResponse} — DTO с публичными полями профиля
     *
     * <p>Аннотации метода:
     * <ul>
     *   <li>{@code @GetMapping("/me")} — HTTP GET /api/users/me.</li>
     *   <li>{@code @AuthenticationPrincipal UserDetails} — Spring Security автоматически
     *       извлекает объект аутентифицированного пользователя из SecurityContext
     *       и передаёт его в параметр метода.</li>
     * </ul>
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public UserResponse getMe(@AuthenticationPrincipal UserDetails userDetails) {
        // Получаем полную сущность User из БД по email (username в терминах Spring Security)
        User user = userService.getUserByEmail(userDetails.getUsername());
        // Конвертируем Entity в DTO с помощью статического фабричного метода
        return UserResponse.from(user);
    }

    /**
     * Обновление профиля текущего пользователя.
     *
     * <p>Позволяет изменить имя, фамилию и краткую биографию.
     * Поля необязательны — можно обновить только нужные.
     *
     * @param userDetails текущий аутентифицированный пользователь
     * @param request     тело запроса с полями для обновления (все необязательные)
     * @return обновлённый {@link UserResponse}
     *
     * <p>{@code @PatchMapping("/me")} — HTTP PATCH /api/users/me.
     * PATCH используется вместо PUT, так как обновляется только часть ресурса.
     */
    @PatchMapping("/me")
    @Operation(summary = "Update current user profile")
    public UserResponse updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        // Находим текущего пользователя, чтобы получить его ID
        User user = userService.getUserByEmail(userDetails.getUsername());
        // Обновляем профиль в сервисном слое и возвращаем новый DTO
        return userService.updateProfile(user.getId(), request);
    }

    /**
     * Загрузка аватара пользователя.
     *
     * <p>Принимает файл изображения через multipart/form-data,
     * сохраняет его на диск (или в облако) и обновляет ссылку на аватар в профиле.
     *
     * @param userDetails текущий аутентифицированный пользователь
     * @param file        загружаемый файл изображения (часть multipart-запроса)
     * @return обновлённый {@link UserResponse} с новым avatarUrl
     * @throws IOException если произошла ошибка чтения или записи файла
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)} —
     *       HTTP POST /api/users/me/avatar, принимает только multipart/form-data запросы.</li>
     *   <li>{@code @RequestPart("file")} — извлекает часть multipart-запроса с именем "file"
     *       как объект {@link MultipartFile}.</li>
     * </ul>
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar image")
    public UserResponse uploadAvatar(@AuthenticationPrincipal UserDetails userDetails,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        // Определяем ID текущего пользователя
        User user = userService.getUserByEmail(userDetails.getUsername());
        // Передаём файл сервису для сохранения и обновления профиля
        return userService.uploadAvatar(user.getId(), file);
    }

    /**
     * Поиск пользователей по имени, фамилии или email.
     *
     * <p>Выполняет регистронезависимый поиск подстроки в полях firstName, lastName и email.
     * Результаты возвращаются постранично (pagination).
     *
     * @param query строка поиска — часть имени или email
     * @param page  номер страницы (начинается с 0), по умолчанию 0
     * @param size  количество результатов на странице, по умолчанию 20
     * @return {@link Page} с {@link UserResponse} — содержит результаты и метаданные страницы
     *
     * <p>{@code @RequestParam(defaultValue = "0")} — если параметр отсутствует в URL,
     * используется значение по умолчанию.
     */
    @GetMapping("/search")
    @Operation(summary = "Search users by name or email")
    public Page<UserResponse> search(@RequestParam String query,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        // Делегируем поиск с пагинацией сервисному слою
        return userService.searchUsers(query, page, size);
    }
}
