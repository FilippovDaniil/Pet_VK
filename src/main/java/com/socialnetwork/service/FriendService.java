package com.socialnetwork.service;

import com.socialnetwork.dto.response.FriendRequestResponse;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.FriendRequest;
import com.socialnetwork.entity.FriendRequestStatus;
import com.socialnetwork.entity.User;
import com.socialnetwork.event.FriendEvent;
import com.socialnetwork.event.FriendEventPublisher;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления дружескими связями между пользователями.
 *
 * <p>Реализует классическую модель «заявка в друзья»:
 * <ol>
 *   <li>Пользователь А отправляет заявку пользователю Б → статус {@code PENDING}</li>
 *   <li>Пользователь Б принимает или отклоняет заявку → статус {@code ACCEPTED} или {@code DECLINED}</li>
 *   <li>Любой из друзей может удалить связь → запись удаляется из БД</li>
 * </ol>
 *
 * <p>Дружба представлена единственной записью {@link FriendRequest} в таблице:
 * по ней можно определить, кто инициатор (requester) и кто получатель (addressee).
 * При получении списка друзей достаточно проверить оба поля, так как
 * пользователь может быть как инициатором, так и получателем.
 *
 * <p>При ключевых событиях (отправка/принятие заявки) публикуются {@link FriendEvent},
 * которые могут обрабатываться другими компонентами (например, отправка уведомлений).
 */
@Service            // Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор с внедрением зависимостей для final-полей
@Slf4j              // Lombok: логгер SLF4J
public class FriendService {

    // Репозиторий для работы с таблицей заявок в друзья
    private final FriendRequestRepository friendRequestRepository;

    // Используем UserService для получения пользователей (там есть кэш — выгодно)
    private final UserService userService;

    // Публикатор событий: позволяет уведомлять систему о действиях с заявками
    private final FriendEventPublisher eventPublisher;

    /**
     * Отправляет заявку в друзья от одного пользователя к другому.
     *
     * <p>Перед созданием проверяется:
     * <ul>
     *   <li>Пользователь не отправляет заявку самому себе</li>
     *   <li>Между пользователями ещё нет никакой заявки (в любом статусе)</li>
     * </ul>
     *
     * <p>После сохранения публикуется событие {@code FRIEND_REQUEST_SENT},
     * которое может быть использовано для отправки push-уведомления.
     *
     * @param requesterId id пользователя, отправляющего заявку
     * @param addresseeId id пользователя, получающего заявку
     * @return DTO созданной заявки с текущим статусом PENDING
     * @throws BadRequestException если id совпадают или заявка уже существует
     */
    @Transactional // Транзакция нужна: и save, и publish должны выполниться атомарно
    public FriendRequestResponse sendRequest(Long requesterId, Long addresseeId) {
        // Бизнес-правило: нельзя дружить с самим собой
        if (requesterId.equals(addresseeId)) {
            throw new BadRequestException("Cannot send friend request to yourself");
        }

        // Загружаем обоих пользователей — убеждаемся, что они существуют в системе
        User requester = userService.getUserById(requesterId);
        User addressee = userService.getUserById(addresseeId);

        // Проверяем, не существует ли уже заявка в любую сторону между этими пользователями.
        // findBetweenUsers ищет запись, где (requester=A, addressee=B) ИЛИ (requester=B, addressee=A)
        friendRequestRepository.findBetweenUsers(requesterId, addresseeId).ifPresent(fr -> {
            // Если заявка нашлась — дублировать нельзя
            throw new BadRequestException("Friend request already exists");
        });

        // Создаём новую заявку со статусом PENDING (ожидает ответа)
        FriendRequest fr = FriendRequest.builder()
                .requester(requester)   // инициатор заявки
                .addressee(addressee)   // получатель заявки
                .status(FriendRequestStatus.PENDING) // начальный статус — «в ожидании»
                .build();

        // Сохраняем заявку в БД — получаем managed-сущность с присвоенным id
        fr = friendRequestRepository.save(fr);

        // Публикуем событие для внешних потребителей (например, WebSocket-уведомление)
        eventPublisher.publish(FriendEvent.of("FRIEND_REQUEST_SENT", requesterId, addresseeId));

        // Конвертируем в DTO и возвращаем клиенту
        return FriendRequestResponse.from(fr);
    }

    /**
     * Возвращает список входящих заявок в друзья для пользователя.
     *
     * <p>Метод помечен {@code readOnly = true}: Spring оптимизирует транзакцию
     * (не отслеживает изменения сущностей), что улучшает производительность.
     *
     * @param userId id пользователя, чьи входящие заявки нужно получить
     * @return список DTO заявок со статусом PENDING, адресованных этому пользователю
     */
    @Transactional(readOnly = true) // readOnly=true: Hibernate не строит dirty checking — быстрее
    public List<FriendRequestResponse> getIncomingRequests(Long userId) {
        // Получаем объект пользователя — нужен как аргумент для репозиторного метода
        User user = userService.getUserById(userId);

        // Ищем все заявки, где данный пользователь является адресатом и статус PENDING
        return friendRequestRepository.findByAddresseeAndStatus(user, FriendRequestStatus.PENDING)
                .stream()
                // Преобразуем каждую сущность FriendRequest в DTO для отправки клиенту
                .map(FriendRequestResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Обрабатывает ответ на заявку в друзья: принятие или отклонение.
     *
     * <p>Только адресат заявки (получатель) вправе её принять или отклонить.
     * Попытка другого пользователя ответить на заявку приведёт к {@link ForbiddenException}.
     *
     * @param requestId     id заявки в друзья
     * @param action        строка действия: «accept» — принять, «reject» — отклонить
     * @param currentUserId id текущего аутентифицированного пользователя
     * @return обновлённое DTO заявки с новым статусом
     * @throws ResourceNotFoundException если заявка не найдена
     * @throws ForbiddenException        если текущий пользователь не является адресатом
     * @throws BadRequestException       если заявка уже обработана или action неизвестен
     */
    @Transactional
    public FriendRequestResponse respondToRequest(Long requestId, String action, Long currentUserId) {
        // Загружаем заявку по id или бросаем 404
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("FriendRequest", requestId));

        // Проверка авторизации: отвечать на заявку может только её получатель (addressee)
        if (!fr.getAddressee().getId().equals(currentUserId)) {
            throw new ForbiddenException("Not allowed to respond to this request");
        }

        // Нельзя обработать уже обработанную заявку (ACCEPTED или DECLINED)
        if (fr.getStatus() != FriendRequestStatus.PENDING) {
            throw new BadRequestException("Request already processed");
        }

        // Обрабатываем действие без учёта регистра: "Accept", "ACCEPT", "accept" — всё допустимо
        if ("accept".equalsIgnoreCase(action)) {
            // Принятие заявки → дружба установлена
            fr.setStatus(FriendRequestStatus.ACCEPTED);
            // Публикуем событие — например, для взаимной подписки в ленте новостей
            eventPublisher.publish(
                    FriendEvent.of("FRIEND_REQUEST_ACCEPTED", fr.getRequester().getId(), currentUserId));

        } else if ("reject".equalsIgnoreCase(action)) {
            // Отклонение заявки → статус DECLINED, запись остаётся в БД (история)
            fr.setStatus(FriendRequestStatus.DECLINED);

        } else {
            // Неизвестное действие — ошибка клиента
            throw new BadRequestException("Unknown action: " + action);
        }

        // Сохраняем обновлённый статус в БД и возвращаем DTO
        return FriendRequestResponse.from(friendRequestRepository.save(fr));
    }

    /**
     * Удаляет дружескую связь между двумя пользователями.
     *
     * <p>Метод работает симметрично: не важно, кто был инициатором заявки —
     * любой из двух пользователей может её удалить.
     *
     * @param userId   id пользователя, инициирующего удаление
     * @param friendId id друга, с которым разрывается связь
     * @throws ResourceNotFoundException если связь не найдена
     * @throws BadRequestException       если пользователи не являются друзьями (статус не ACCEPTED)
     */
    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        // Ищем запись заявки в любую сторону между этими пользователями
        FriendRequest fr = friendRequestRepository.findBetweenUsers(userId, friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));

        // Удалять можно только принятую заявку (активную дружбу)
        // Если статус PENDING или DECLINED — это не дружба
        if (fr.getStatus() != FriendRequestStatus.ACCEPTED) {
            throw new BadRequestException("Users are not friends");
        }

        // Физически удаляем запись из БД — дружба прекращена
        friendRequestRepository.delete(fr);
    }

    /**
     * Возвращает список всех друзей пользователя.
     *
     * <p>Поскольку дружба хранится как одна запись {@link FriendRequest}, а пользователь
     * может быть как requester, так и addressee, нам нужно определить,
     * кто является «другим» пользователем в каждой паре.
     *
     * @param userId id пользователя, чей список друзей запрашивается
     * @return список DTO профилей друзей
     */
    @Transactional(readOnly = true) // Только чтение — оптимизация транзакции
    public List<UserResponse> getFriends(Long userId) {
        // Получаем все записи FriendRequest со статусом ACCEPTED, где участвует данный пользователь
        return friendRequestRepository.findFriendsByUserId(userId).stream()
                .map(fr -> {
                    // Определяем, кто из двух участников записи является «другом»:
                    // если текущий пользователь — инициатор, то друг — адресат, и наоборот
                    User friend = fr.getRequester().getId().equals(userId)
                            ? fr.getAddressee()  // текущий пользователь отправлял заявку
                            : fr.getRequester(); // текущий пользователь получал заявку
                    // Преобразуем сущность друга в DTO
                    return UserResponse.from(friend);
                })
                .collect(Collectors.toList());
    }
}
