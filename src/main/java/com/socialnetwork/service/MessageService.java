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

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserService userService;

    @Transactional
    public MessageResponse sendMessage(Long senderId, MessageRequest request) {
        if (senderId.equals(request.getRecipientId())) {
            throw new BadRequestException("Cannot send message to yourself");
        }
        User sender = userService.getUserById(senderId);
        User recipient = userService.getUserById(request.getRecipientId());
        if (recipient.isBanned()) {
            throw new BadRequestException("Recipient is banned");
        }
        Message message = Message.builder()
                .sender(sender)
                .recipient(recipient)
                .text(request.getText())
                .build();
        return MessageResponse.from(messageRepository.save(message));
    }

    @Transactional
    public Page<MessageResponse> getDialog(Long currentUserId, Long otherUserId, int page, int size) {
        messageRepository.markAsRead(currentUserId, otherUserId);
        return messageRepository.findDialog(currentUserId, otherUserId, PageRequest.of(page, size))
                .map(MessageResponse::from);
    }
}
