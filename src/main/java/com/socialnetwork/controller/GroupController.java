package com.socialnetwork.controller;

import com.socialnetwork.dto.request.GroupCreateRequest;
import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.GroupResponse;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.service.GroupService;
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

import java.util.List;

/**
 * Контроллер управления группами (сообществами).
 *
 * <p>Реализует функциональность групп, аналогичную ВКонтакте: создание,
 * просмотр, вступление и выход, управление администраторами, публикация постов.
 * Группа — это сообщество с владельцем, администраторами и участниками.
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — REST-контроллер, методы возвращают JSON.</li>
 *   <li>{@code @RequestMapping("/api/groups")} — базовый URL для всех эндпоинтов.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok создаёт конструктор для final-полей.</li>
 *   <li>{@code @Tag(name = "Groups")} — группировка эндпоинтов в Swagger UI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Groups")
public class GroupController {

    // Сервис бизнес-логики групп: создание, вступление, управление постами и администраторами
    private final GroupService groupService;

    // Сервис пользователей: для получения ID текущего пользователя по email
    private final UserService userService;

    /**
     * Создание новой группы.
     *
     * <p>Создаёт группу с текущим пользователем в роли владельца (OWNER).
     * Владелец автоматически добавляется в участники и получает права администратора.
     *
     * @param userDetails данные текущего пользователя (станет владельцем группы)
     * @param request     тело запроса: название группы (обязательно), описание и аватар (опционально)
     * @return {@link GroupResponse} с данными созданной группы
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @PostMapping} без пути — HTTP POST /api/groups.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.CREATED)} — код ответа 201 Created.</li>
     *   <li>{@code @Valid} — запускает валидацию полей запроса.</li>
     * </ul>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group")
    public GroupResponse createGroup(@AuthenticationPrincipal UserDetails userDetails,
                                     @Valid @RequestBody GroupCreateRequest request) {
        // Получаем ID пользователя — он будет установлен как владелец группы
        Long userId = getId(userDetails);
        return groupService.createGroup(userId, request);
    }

    /**
     * Получение информации о группе по ID.
     *
     * <p>Возвращает публичную информацию о группе: название, описание, аватар,
     * данные владельца и дату создания. Доступно для всех аутентифицированных пользователей.
     *
     * @param groupId ID запрашиваемой группы (из пути URL)
     * @return {@link GroupResponse} с информацией о группе
     *
     * <p>{@code @GetMapping("/{groupId}")} — HTTP GET /api/groups/{groupId}.
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "Get group info")
    public GroupResponse getGroup(@PathVariable Long groupId) {
        return groupService.getGroup(groupId);
    }

    /**
     * Получение списка групп текущего пользователя.
     *
     * @param userDetails данные текущего пользователя
     * @return список {@link GroupResponse} с группами, в которых состоит пользователь
     */
    @GetMapping("/my")
    @Operation(summary = "Get groups of current user")
    public List<GroupResponse> getMyGroups(@AuthenticationPrincipal UserDetails userDetails) {
        return groupService.getUserGroups(getId(userDetails));
    }

    /**
     * Вступление в группу.
     *
     * <p>Добавляет текущего пользователя в участники группы с ролью MEMBER.
     * Если пользователь уже является участником — сервис выбросит BadRequestException.
     *
     * @param userDetails данные текущего пользователя
     * @param groupId     ID группы для вступления
     */
    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Join a group")
    public void join(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long groupId) {
        groupService.joinGroup(getId(userDetails), groupId);
    }

    /**
     * Выход из группы.
     *
     * <p>Удаляет текущего пользователя из списка участников группы.
     * Владелец группы не может покинуть её — нужно сначала передать права или удалить группу.
     *
     * @param userDetails данные текущего пользователя
     * @param groupId     ID группы для выхода
     */
    @DeleteMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave a group")
    public void leave(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long groupId) {
        groupService.leaveGroup(getId(userDetails), groupId);
    }

    /**
     * Назначение участника группы администратором.
     *
     * <p>Только действующий администратор или владелец группы может назначить
     * другого участника администратором (повысить роль MEMBER -> ADMIN).
     *
     * @param userDetails данные текущего пользователя (должен быть admin/owner группы)
     * @param groupId     ID группы
     * @param userId      ID участника, которого нужно сделать администратором
     *
     * <p>{@code @PostMapping("/{groupId}/admins/{userId}")} — HTTP POST /api/groups/{groupId}/admins/{userId}.
     */
    @PostMapping("/{groupId}/admins/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Promote user to group admin (current admin only)")
    public void addAdmin(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long groupId,
                         @PathVariable Long userId) {
        // Проверяем права и повышаем роль указанного участника до администратора
        groupService.addGroupAdmin(getId(userDetails), groupId, userId);
    }

    /**
     * Удаление группы.
     *
     * <p>Удалить группу может только её владелец или суперадминистратор системы.
     * При удалении группы каскадно удаляются все её участники и посты.
     *
     * @param userDetails данные текущего пользователя
     * @param groupId     ID удаляемой группы
     *
     * <p>{@code @DeleteMapping("/{groupId}")} — HTTP DELETE /api/groups/{groupId}.
     */
    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete group (owner or super-admin)")
    public void deleteGroup(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long groupId) {
        // Удаляем группу с проверкой прав текущего пользователя
        groupService.deleteGroup(getId(userDetails), groupId);
    }

    /**
     * Публикация поста в группе.
     *
     * <p>Только участники группы с ролью ADMIN или OWNER могут публиковать посты
     * от имени группы. Обычные участники (MEMBER) не имеют права на публикацию.
     *
     * @param userDetails данные текущего пользователя
     * @param groupId     ID группы, в которую публикуется пост
     * @param request     тело запроса: текст и опциональная ссылка на изображение
     * @return {@link PostResponse} с данными опубликованного поста
     *
     * <p>{@code @PostMapping("/{groupId}/posts")} — HTTP POST /api/groups/{groupId}/posts.
     */
    @PostMapping("/{groupId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create post in group")
    public PostResponse createGroupPost(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long groupId,
                                        @Valid @RequestBody PostCreateRequest request) {
        // Создаём пост от имени текущего пользователя в указанной группе
        return groupService.createGroupPost(getId(userDetails), groupId, request);
    }

    /**
     * Получение постов группы с пагинацией.
     *
     * <p>Возвращает посты группы в порядке от новых к старым.
     * Доступно для всех аутентифицированных пользователей.
     *
     * @param groupId ID группы
     * @param page    номер страницы (0-based), по умолчанию 0
     * @param size    количество постов на странице, по умолчанию 10
     * @return {@link Page} с {@link PostResponse}
     *
     * <p>{@code @GetMapping("/{groupId}/posts")} — HTTP GET /api/groups/{groupId}/posts.
     */
    @GetMapping("/{groupId}/posts")
    @Operation(summary = "Get posts in group (paginated)")
    public Page<PostResponse> getGroupPosts(@PathVariable Long groupId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return groupService.getGroupPosts(groupId, page, size);
    }

    /**
     * Вспомогательный метод для получения числового ID текущего пользователя.
     *
     * <p>Spring Security хранит email как {@code username} в {@link UserDetails}.
     * Метод загружает пользователя из БД по email и возвращает его первичный ключ.
     *
     * @param userDetails детали аутентифицированного пользователя
     * @return числовой ID текущего пользователя
     */
    private Long getId(UserDetails userDetails) {
        // userDetails.getUsername() возвращает email — по нему ищем пользователя в БД
        return userService.getUserByEmail(userDetails.getUsername()).getId();
    }
}
