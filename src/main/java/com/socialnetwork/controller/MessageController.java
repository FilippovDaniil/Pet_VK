package com.socialnetwork.controller;

import com.socialnetwork.dto.request.MessageRequest;
import com.socialnetwork.dto.response.MessageResponse;
import com.socialnetwork.service.MessageService;
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
 * Контроллер личных сообщений.
 *
 * <p>Реализует функциональность личной переписки между двумя пользователями
 * (аналог ВКонтакте ЛС). Поддерживает отправку сообщений и постраничное
 * получение диалога с автоматической отметкой о прочтении.
 *
 * <p>Аннотации класса:
 * <ul>
 *   <li>{@code @RestController} — REST-контроллер, возвращает JSON.</li>
 *   <li>{@code @RequestMapping("/api/messages")} — базовый URL для всех эндпоинтов.</li>
 *   <li>{@code @RequiredArgsConstructor} — Lombok генерирует конструктор для final-полей.</li>
 *   <li>{@code @Tag(name = "Messages")} — группировка в Swagger UI.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messages")
public class MessageController {

    // Сервис работы с сообщениями: отправка, получение диалога, отметка о прочтении
    private final MessageService messageService;

    // Сервис пользователей: нужен для получения ID текущего пользователя
    private final UserService userService;

    /**
     * Отправка личного сообщения другому пользователю.
     *
     * <p>Создаёт новое сообщение от текущего пользователя указанному получателю.
     * Сообщение сохраняется в базе данных со статусом "непрочитано".
     *
     * @param userDetails данные текущего пользователя (отправитель)
     * @param request     тело запроса: ID получателя и текст сообщения (до 10 000 символов)
     * @return {@link MessageResponse} с данными сохранённого сообщения
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @PostMapping} без пути — HTTP POST /api/messages.</li>
     *   <li>{@code @ResponseStatus(HttpStatus.CREATED)} — возвращает статус 201 Created.</li>
     *   <li>{@code @Valid} — запускает валидацию полей запроса (@NotNull, @NotBlank, @Size).</li>
     *   <li>{@code @RequestBody} — десериализует JSON из тела запроса в {@link MessageRequest}.</li>
     * </ul>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message")
    public MessageResponse sendMessage(@AuthenticationPrincipal UserDetails userDetails,
                                       @Valid @RequestBody MessageRequest request) {
        // Получаем числовой ID отправителя по его email из Security Context
        Long senderId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Создаём сообщение через сервис и возвращаем DTO
        return messageService.sendMessage(senderId, request);
    }

    /**
     * Получение диалога с пользователем (с пагинацией).
     *
     * <p>Возвращает все сообщения между текущим пользователем и указанным,
     * отсортированные от новых к старым. При загрузке диалога сообщения
     * автоматически отмечаются как прочитанные.
     *
     * @param userDetails данные текущего пользователя
     * @param userId      ID второго участника диалога (из пути URL)
     * @param page        номер страницы (0-based), по умолчанию 0
     * @param size        количество сообщений на странице, по умолчанию 20
     * @return {@link Page} с {@link MessageResponse} — сообщения диалога с метаданными пагинации
     *
     * <p>Аннотации:
     * <ul>
     *   <li>{@code @GetMapping("/{userId}")} — HTTP GET /api/messages/{userId}.</li>
     *   <li>{@code @PathVariable Long userId} — ID собеседника извлекается из URL.</li>
     *   <li>{@code @RequestParam(defaultValue = ...)} — параметры пагинации со значениями по умолчанию.</li>
     * </ul>
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get dialog with user (paginated, marks as read)")
    public Page<MessageResponse> getDialog(@AuthenticationPrincipal UserDetails userDetails,
                                            @PathVariable Long userId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        // Определяем ID текущего пользователя для выборки двусторонней переписки
        Long currentId = userService.getUserByEmail(userDetails.getUsername()).getId();
        // Получаем диалог; сервис также отмечает непрочитанные сообщения как прочитанные
        return messageService.getDialog(currentId, userId, page, size);
    }
}
