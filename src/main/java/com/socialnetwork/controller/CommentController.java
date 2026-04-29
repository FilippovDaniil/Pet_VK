package com.socialnetwork.controller;

import com.socialnetwork.dto.request.CommentCreateRequest;
import com.socialnetwork.dto.response.CommentResponse;
import com.socialnetwork.service.CommentService;
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
 * Контроллер комментариев к постам.
 *
 * <p>Позволяет аутентифицированным пользователям оставлять комментарии к записям,
 * просматривать комментарии под постом и удалять собственные комментарии.
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — REST-контроллер Spring MVC, возвращает JSON.</li>
 *   <li>{@code @RequestMapping("/api/comments")} — базовый URL-путь.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok создаёт конструктор для final-зависимостей.</li>
 *   <li>{@code @Tag(name = "Comments")} — группа в документации Swagger/OpenAPI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "Comments")
public class CommentController {

    // Сервис бизнес-логики комментариев: добавление, получение, удаление
    private final CommentService commentService;

    // Сервис пользователей: необходим для получения ID текущего пользователя
    private final UserService userService;

    /**
     * Добавление комментария к посту.
     *
     * <p>Текущий аутентифицированный пользователь оставляет комментарий
     * к посту, указанному в теле запроса. Комментарий сохраняется в базе данных.
     *
     * @param userDetails данные текущего пользователя из Security Context
     * @param request     тело запроса: ID поста и текст комментария (до 5 000 символов)
     * @return {@link CommentResponse} с данными созданного комментария
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @PostMapping} без пути — HTTP POST /api/comments.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.CREATED)} — код ответа 201 Created.</li>
     *   <li>{@code @Valid} — запускает Bean Validation (@NotNull, @NotBlank, @Size).</li>
     *   <li>{@code @RequestBody} — читает JSON из тела HTTP-запроса.</li>
     * </ul>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add comment to a post")
    public CommentResponse addComment(@AuthenticationPrincipal UserDetails userDetails,
                                      @Valid @RequestBody CommentCreateRequest request) {
        // Получаем ID текущего пользователя — он будет автором комментария
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Создаём комментарий через сервис
        return commentService.addComment(userId, request);
    }

    /**
     * Получение комментариев к посту с пагинацией.
     *
     * <p>Возвращает комментарии в хронологическом порядке (от старых к новым).
     * Доступно для всех аутентифицированных пользователей без ограничений.
     *
     * @param postId ID поста, комментарии которого нужно получить (из пути URL)
     * @param page   номер страницы (0-based), по умолчанию 0
     * @param size   количество комментариев на странице, по умолчанию 20
     * @return {@link Page} с {@link CommentResponse} — список комментариев с метаданными
     *
     * <p>{@code @GetMapping("/{postId}")} — HTTP GET /api/comments/{postId}.
     */
    @GetMapping("/{postId}")
    @Operation(summary = "Get comments for a post (paginated)")
    public Page<CommentResponse> getComments(@PathVariable Long postId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        // Запрашиваем постраничный список комментариев к посту
        return commentService.getComments(postId, page, size);
    }

    /**
     * Удаление собственного комментария.
     *
     * <p>Пользователь может удалить только свой комментарий.
     * Сервисный слой проверяет право на удаление (владение или роль администратора).
     *
     * @param userDetails данные текущего пользователя
     * @param commentId   ID удаляемого комментария (из пути URL)
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @DeleteMapping("/{commentId}")} — HTTP DELETE /api/comments/{commentId}.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.NO_CONTENT)} — ответ 204 без тела при успехе.</li>
     * </ul>
     */
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own comment")
    public void deleteComment(@AuthenticationPrincipal UserDetails userDetails,
                               @PathVariable Long commentId) {
        // Получаем ID текущего пользователя для проверки, что удаляет свой комментарий
        Long userId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Удаляем комментарий с проверкой прав
        commentService.deleteComment(commentId, userId);
    }
}
