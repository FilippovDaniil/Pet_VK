package com.socialnetwork.controller;

import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.service.PostService;
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

/**
 * Контроллер управления постами (записями на стене).
 *
 * <p>Реализует функциональность стены пользователя, аналогичную ВКонтакте:
 * создание поста на собственной стене, просмотр стены любого пользователя,
 * редактирование и удаление собственных записей.
 *
 * <p>Посты в группах управляются через {@code GroupController}.
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — REST-контроллер, методы возвращают JSON автоматически.</li>
 *   <li>{@code @RequestMapping("/api/posts")} — базовый путь всех эндпоинтов контроллера.</li>
 *   <li>{@code @RequiredArgsConstructor} — конструктор для final-полей генерируется Lombok.</li>
 *   <li>{@code @Tag(name = "Posts")} — метка группы в Swagger UI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts")
public class PostController {

    // Сервис бизнес-логики постов: создание, получение, обновление, удаление
    private final PostService postService;

    // Сервис пользователей: нужен для получения ID текущего пользователя по email
    private final UserService userService;

    /**
     * Создание поста на собственной стене.
     *
     * <p>Аутентифицированный пользователь публикует запись только на своей стене.
     * Пост может содержать текст и опциональную ссылку на изображение.
     *
     * @param userDetails данные текущего пользователя из Security Context
     * @param request     тело запроса: текст поста (обязателен, до 10 000 символов) и imageUrl (опционально)
     * @return {@link PostResponse} с данными созданного поста
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @PostMapping("/wall")} — HTTP POST /api/posts/wall.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.CREATED)} — ответ 201 Created при успехе.</li>
     *   <li>{@code @Valid} — включает Bean Validation для полей {@code request}.</li>
     *   <li>{@code @AuthenticationPrincipal UserDetails} — Spring Security подставляет
     *       текущего аутентифицированного пользователя из SecurityContextHolder.</li>
     * </ul>
     */
    @PostMapping("/wall")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create post on own wall")
    public PostResponse createPost(@AuthenticationPrincipal UserDetails userDetails,
                                   @Valid @RequestBody PostCreateRequest request) {
        // Получаем ID текущего пользователя через сервис (email — это username в Spring Security)
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Создаём пост на стене и возвращаем DTO
        return postService.createWallPost(userId, request);
    }

    /**
     * Получение стены пользователя (с пагинацией).
     *
     * <p>Возвращает посты конкретного пользователя в порядке от новых к старым.
     * Доступно для всех аутентифицированных пользователей — можно смотреть
     * стену любого человека.
     *
     * @param userId ID пользователя, чья стена запрашивается (из пути URL)
     * @param page   номер страницы (0-based), по умолчанию 0
     * @param size   размер страницы, по умолчанию 10
     * @return {@link Page} с {@link PostResponse} — содержит список постов и метаданные пагинации
     *
     * <p>{@code @PathVariable Long userId} — извлекает значение {userId} из URL-шаблона.
     */
    @GetMapping("/wall/{userId}")
    @Operation(summary = "Get wall posts of a user (paginated)")
    public Page<PostResponse> getWall(@PathVariable Long userId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        // Запрашиваем посты стены с пагинацией
        return postService.getWallPosts(userId, page, size);
    }

    /**
     * Редактирование собственного поста.
     *
     * <p>Пользователь может изменить текст и/или ссылку на изображение.
     * Сервисный слой проверяет, что редактируемый пост принадлежит текущему пользователю.
     *
     * @param userDetails данные текущего пользователя
     * @param postId      ID редактируемого поста (из пути URL)
     * @param request     новые данные поста
     * @return обновлённый {@link PostResponse}
     *
     * <p>{@code @PutMapping("/{postId}")} — HTTP PUT /api/posts/{postId}.
     * PUT заменяет ресурс целиком (в отличие от PATCH, который обновляет частично).
     */
    @PutMapping("/{postId}")
    @Operation(summary = "Edit own post")
    public PostResponse updatePost(@AuthenticationPrincipal UserDetails userDetails,
                                   @PathVariable Long postId,
                                   @Valid @RequestBody PostCreateRequest request) {
        // Получаем ID текущего пользователя для проверки прав владения постом
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Обновляем пост; сервис выбросит ForbiddenException если пост чужой
        return postService.updatePost(postId, userId, request);
    }

    /**
     * Удаление собственного поста.
     *
     * <p>Пользователь удаляет только свои посты. Если пост не найден или
     * принадлежит другому пользователю — сервис выбросит соответствующее исключение.
     *
     * @param userDetails данные текущего пользователя
     * @param postId      ID удаляемого поста
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @DeleteMapping("/{postId}")} — HTTP DELETE /api/posts/{postId}.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.NO_CONTENT)} — ответ 204 без тела при успехе.</li>
     * </ul>
     */
    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own post")
    public void deletePost(@AuthenticationPrincipal UserDetails userDetails,
                           @PathVariable Long postId) {
        // Определяем ID текущего пользователя для проверки права удаления
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Удаляем пост; метод void, возвращается 204 No Content
        postService.deletePost(postId, userId);
    }
}
