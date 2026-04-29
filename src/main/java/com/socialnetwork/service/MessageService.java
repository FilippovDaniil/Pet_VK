package com.socialnetwork.service;

import com.socialnetwork.dto.request.MessageRequest;
import com.socialnetwork.dto.response.MessageResponse;
import com.socialnetwork.entity.Message;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис личных сообщений между пользователями.
 *
 * <p>Реализует функциональность приватной переписки в формате «один-на-один».
 * Каждое сообщение имеет отправителя, получателя, текст и флаг прочтения.
 *
 * <p>При получении диалога непрочитанные сообщения автоматически помечаются
 * как прочитанные — это позволяет отображать актуальный счётчик непрочитанных.
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-полей (внедрение зависимостей)
public class MessageService {

    // Репозиторий JPA для операций с таблицей сообщений
    private final MessageRepository messageRepository;

    // Сервис пользователей: получение отправителя и получателя (с кэшем)
    private final UserService userService;

    /**
     * Отправляет личное сообщение другому пользователю.
     *
     * <p>Перед сохранением проверяется:
     * <ul>
     *   <li>Пользователь не отправляет сообщение сам себе</li>
     *   <li>Получатель не заблокирован (нельзя писать заблокированным)</li>
     * </ul>
     *
     * @param senderId id отправителя (текущий аутентифицированный пользователь)
     * @param request  DTO с id получателя и текстом сообщения
     * @return DTO сохранённого сообщения
     * @throws BadRequestException если получатель — сам отправитель или заблокирован
     */
    @Transactional // Транзакция: сохранение сообщения должно быть атомарным
    public MessageResponse sendMessage(Long senderId, MessageRequest request) {
        // Нельзя отправить сообщение самому себе — бизнес-правило социальной сети
        if (senderId.equals(request.getRecipientId())) {
            throw new BadRequestException("Cannot send message to yourself");
        }

        // Загружаем отправителя и получателя — проверяем их существование
        User sender = userService.getUserById(senderId);
        User recipient = userService.getUserById(request.getRecipientId());

        // Заблокированным пользователям нельзя писать — они отключены от общения на платформе
        if (recipient.isBanned()) {
            throw new BadRequestException("Recipient is banned");
        }

        // Создаём сущность сообщения через Builder
        // read = false устанавливается по умолчанию (аннотация @Builder.Default в Message)
        Message message = Message.builder()
                .sender(sender)
                .recipient(recipient)
                .text(request.getText())
                .build();

        // Сохраняем и возвращаем DTO с заполненным id и createdAt
        return MessageResponse.from(messageRepository.save(message));
    }

    /**
     * Возвращает диалог между двумя пользователями с пагинацией.
     *
     * <p>При каждом обращении к диалогу непрочитанные сообщения от собеседника
     * автоматически помечаются прочитанными. Это позволяет корректно обновлять
     * счётчик непрочитанных на клиенте.
     *
     * <p>Порядок сообщений: от новых к старым (DESC по дате), стандартный
     * для мессенджеров — последние сообщения видны первыми.
     *
     * @param currentUserId id текущего пользователя
     * @param otherUserId   id собеседника
     * @param page          номер страницы (начиная с 0)
     * @param size          количество сообщений на странице
     * @return страница с DTO сообщений диалога
     */
    @Transactional // Транзакция нужна: markAsRead (UPDATE) и findDialog (SELECT) должны быть атомарны
    public Page<MessageResponse> getDialog(Long currentUserId, Long otherUserId, int page, int size) {
        // Отмечаем все непрочитанные сообщения от otherUser к currentUser как прочитанные.
        // Это делается ПЕРЕД возвратом диалога: пользователь «прочитал» сообщения, открыв чат.
        messageRepository.markAsRead(currentUserId, otherUserId);

        // Получаем двустороннюю переписку: сообщения как от currentUser к otherUser,
        // так и от otherUser к currentUser, отсортированные по дате
        return messageRepository.findDialog(currentUserId, otherUserId, PageRequest.of(page, size))
                .map(MessageResponse::from);
    }
}
