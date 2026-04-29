package com.socialnetwork.controller;

import com.socialnetwork.dto.response.FriendRequestResponse;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.service.FriendService;
import com.socialnetwork.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер управления друзьями.
 *
 * <p>Реализует полный жизненный цикл дружбы в социальной сети:
 * <ol>
 *   <li>Отправка заявки в друзья</li>
 *   <li>Просмотр входящих заявок</li>
 *   <li>Принятие или отклонение заявки</li>
 *   <li>Удаление из друзей</li>
 *   <li>Получение списка друзей</li>
 * </ol>
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — REST-контроллер, результаты методов сериализуются в JSON.</li>
 *   <li>{@code @RequestMapping("/api/friends")} — базовый URL для всех эндпоинтов.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok создаёт конструктор для final-полей.</li>
 *   <li>{@code @Tag(name = "Friends")} — группировка в документации Swagger.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Tag(name = "Friends")
public class FriendController {

    // Сервис логики дружбы: заявки, принятие, удаление, список друзей
    private final FriendService friendService;

    // Сервис пользователей: нужен для получения ID текущего пользователя по email
    private final UserService userService;

    /**
     * Отправка заявки в друзья другому пользователю.
     *
     * <p>Создаёт запись {@code FriendRequest} со статусом PENDING.
     * Нельзя отправить заявку самому себе или повторно, если уже есть активная заявка.
     *
     * @param userDetails данные текущего пользователя (отправитель)
     * @param userId      ID пользователя, которому отправляется заявка (получатель)
     * @return {@link FriendRequestResponse} с деталями созданной заявки
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @PostMapping("/requests/{userId}")} — HTTP POST /api/friends/requests/{userId}.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.CREATED)} — ответ 201 Created при создании заявки.</li>
     *   <li>{@code @PathVariable Long userId} — извлекает {userId} из пути URL.</li>
     * </ul>
     */
    @PostMapping("/requests/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send friend request")
    public FriendRequestResponse sendRequest(@AuthenticationPrincipal UserDetails userDetails,
                                              @PathVariable Long userId) {
        // Получаем ID текущего пользователя (он будет отправителем заявки)
        Long currentId = getCurrentUserId(userDetails);
        // Создаём заявку в друзья через сервис
        return friendService.sendRequest(currentId, userId);
    }

    /**
     * Получение списка входящих заявок в друзья.
     *
     * <p>Возвращает все заявки в статусе PENDING, адресованные текущему пользователю.
     *
     * @param userDetails данные текущего пользователя
     * @return список {@link FriendRequestResponse} с входящими заявками
     *
     * <p>{@code @GetMapping("/requests/incoming")} — HTTP GET /api/friends/requests/incoming.
     */
    @GetMapping("/requests/incoming")
    @Operation(summary = "Get incoming friend requests")
    public List<FriendRequestResponse> getIncoming(@AuthenticationPrincipal UserDetails userDetails) {
        // Загружаем все ожидающие заявки для текущего пользователя
        return friendService.getIncomingRequests(getCurrentUserId(userDetails));
    }

    /**
     * Ответ на заявку в друзья: принять или отклонить.
     *
     * <p>Изменяет статус заявки: {@code PENDING -> ACCEPTED} или {@code PENDING -> REJECTED}.
     * При принятии оба пользователя становятся друзьями.
     *
     * @param userDetails данные текущего пользователя (должен быть получателем заявки)
     * @param requestId   ID заявки в друзья
     * @param action      строка "accept" для принятия или "reject" для отклонения
     * @return обновлённый {@link FriendRequestResponse} с новым статусом
     *
     * <p>{@code @PutMapping("/requests/{requestId}")} — HTTP PUT /api/friends/requests/{requestId}.
     * {@code @RequestParam String action} — параметр из строки запроса (?action=accept).
     */
    @PutMapping("/requests/{requestId}")
    @Operation(summary = "Accept or reject friend request (action=accept|reject)")
    public FriendRequestResponse respond(@AuthenticationPrincipal UserDetails userDetails,
                                          @PathVariable Long requestId,
                                          @RequestParam String action) {
        // Обрабатываем ответ на заявку с проверкой, что текущий пользователь — её адресат
        return friendService.respondToRequest(requestId, action, getCurrentUserId(userDetails));
    }

    /**
     * Удаление пользователя из списка друзей.
     *
     * <p>Разрывает дружеские отношения: запись о дружбе удаляется или переводится
     * в неактивный статус. Действие взаимное — оба пользователя перестают быть друзьями.
     *
     * @param userDetails данные текущего пользователя
     * @param friendId    ID пользователя, которого нужно удалить из друзей
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @DeleteMapping("/{friendId}")} — HTTP DELETE /api/friends/{friendId}.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.NO_CONTENT)} — ответ 204 без тела.</li>
     * </ul>
     */
    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove friend")
    public void removeFriend(@AuthenticationPrincipal UserDetails userDetails,
                             @PathVariable Long friendId) {
        // Удаляем связь дружбы между текущим пользователем и указанным
        friendService.removeFriend(getCurrentUserId(userDetails), friendId);
    }

    /**
     * Получение списка всех друзей текущего пользователя.
     *
     * <p>Возвращает пользователей, с которыми текущий пользователь имеет
     * принятые (ACCEPTED) заявки в друзья (с любой из сторон).
     *
     * @param userDetails данные текущего пользователя
     * @return список {@link UserResponse} — друзья текущего пользователя
     *
     * <p>{@code @GetMapping} без пути — обрабатывает GET /api/friends.
     */
    @GetMapping
    @Operation(summary = "Get friend list")
    public List<UserResponse> getFriends(@AuthenticationPrincipal UserDetails userDetails) {
        // Получаем список друзей из сервиса
        return friendService.getFriends(getCurrentUserId(userDetails));
    }

    /**
     * Вспомогательный метод для получения ID текущего пользователя.
     *
     * <p>Извлекает email из {@link UserDetails} (Spring Security хранит email как username)
     * и загружает пользователя из базы данных, чтобы получить его числовой ID.
     *
     * @param userDetails детали аутентифицированного пользователя из Security Context
     * @return числовой ID текущего пользователя в базе данных
     */
    private Long getCurrentUserId(UserDetails userDetails) {
        // userDetails.getUsername() возвращает email, по которому ищем пользователя в БД
        return userService.getUserByEmail(userDetails.getUsername()).getId();
    }
}
