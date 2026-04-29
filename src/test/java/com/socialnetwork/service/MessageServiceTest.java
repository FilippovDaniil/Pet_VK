package com.socialnetwork.service;

import com.socialnetwork.dto.request.MessageRequest;
import com.socialnetwork.dto.response.MessageResponse;
import com.socialnetwork.entity.Message;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link MessageService}.
 *
 * <p>Проверяет отправку личных сообщений и загрузку диалога с авто-прочтением.
 * Тест {@code getDialog_marksMessagesAsRead} проверяет ключевой побочный эффект:
 * при запросе диалога все непрочитанные входящие сообщения помечаются как прочитанные.
 *
 * <p>Покрываемые сценарии:
 * <ul>
 *   <li>sendMessage: успех с проверкой всех полей ответа</li>
 *   <li>sendMessage самому себе → BadRequestException</li>
 *   <li>sendMessage заблокированному пользователю → BadRequestException</li>
 *   <li>sendMessage: проверка правильности имени отправителя в ответе</li>
 *   <li>getDialog: markAsRead вызывается перед возвратом; двунаправленные сообщения; пустой диалог</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock UserService userService;

    @InjectMocks MessageService messageService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User createUser(Long id, Role role) {
        return User.builder()
                .id(id)
                .email("user" + id + "@test.com")
                .firstName("First" + id)
                .lastName("Last" + id)
                .role(role)
                .banned(false)
                .build();
    }

    private User createBannedUser(Long id) {
        return User.builder()
                .id(id)
                .email("banned" + id + "@test.com")
                .firstName("Banned")
                .lastName("User")
                .role(Role.ROLE_USER)
                .banned(true)
                .build();
    }

    private MessageRequest createRequest(Long recipientId, String text) {
        MessageRequest req = new MessageRequest();
        req.setRecipientId(recipientId);
        req.setText(text);
        return req;
    }

    private Message buildMessage(Long id, User sender, User recipient, String text) {
        return Message.builder()
                .id(id)
                .sender(sender)
                .recipient(recipient)
                .text(text)
                .read(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // sendMessage
    // -------------------------------------------------------------------------

    @Test
    void sendMessage_success() {
        User sender = createUser(1L, Role.ROLE_USER);
        User recipient = createUser(2L, Role.ROLE_USER);
        MessageRequest request = createRequest(2L, "Hello!");

        when(userService.getUserById(1L)).thenReturn(sender);
        when(userService.getUserById(2L)).thenReturn(recipient);

        Message savedMessage = buildMessage(77L, sender, recipient, "Hello!");
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        MessageResponse response = messageService.sendMessage(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(77L);
        assertThat(response.getSenderId()).isEqualTo(1L);
        assertThat(response.getRecipientId()).isEqualTo(2L);
        assertThat(response.getText()).isEqualTo("Hello!");
        assertThat(response.isRead()).isFalse();

        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_toSelf_throws() {
        MessageRequest request = createRequest(1L, "Talking to myself");

        assertThatThrownBy(() -> messageService.sendMessage(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yourself");

        verifyNoInteractions(userService, messageRepository);
    }

    @Test
    void sendMessage_toBannedUser_throws() {
        User sender = createUser(1L, Role.ROLE_USER);
        User bannedRecipient = createBannedUser(3L);
        MessageRequest request = createRequest(3L, "Hi there");

        when(userService.getUserById(1L)).thenReturn(sender);
        when(userService.getUserById(3L)).thenReturn(bannedRecipient);

        assertThatThrownBy(() -> messageService.sendMessage(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("banned");

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_storesCorrectFields() {
        User sender = createUser(10L, Role.ROLE_USER);
        User recipient = createUser(20L, Role.ROLE_USER);
        MessageRequest request = createRequest(20L, "Test message content");

        when(userService.getUserById(10L)).thenReturn(sender);
        when(userService.getUserById(20L)).thenReturn(recipient);

        Message savedMessage = buildMessage(1L, sender, recipient, "Test message content");
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        MessageResponse response = messageService.sendMessage(10L, request);

        assertThat(response.getSenderName()).isEqualTo("First10 Last10");
        assertThat(response.getText()).isEqualTo("Test message content");
    }

    // -------------------------------------------------------------------------
    // getDialog
    // -------------------------------------------------------------------------

    @Test
    void getDialog_marksMessagesAsRead() {
        User u1 = createUser(1L, Role.ROLE_USER);
        User u2 = createUser(2L, Role.ROLE_USER);

        List<Message> messages = List.of(
                buildMessage(1L, u2, u1, "Hey"),
                buildMessage(2L, u1, u2, "Hi"),
                buildMessage(3L, u2, u1, "How are you?")
        );
        Page<Message> page = new PageImpl<>(messages, PageRequest.of(0, 20), 3);

        when(messageRepository.findDialog(eq(1L), eq(2L), any())).thenReturn(page);

        Page<MessageResponse> result = messageService.getDialog(1L, 2L, 0, 20);

        // markAsRead must have been called before returning messages
        verify(messageRepository).markAsRead(1L, 2L);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void getDialog_returnsMessagesFromBothDirections() {
        User u1 = createUser(1L, Role.ROLE_USER);
        User u2 = createUser(2L, Role.ROLE_USER);

        // Messages sent by u1 to u2 and u2 to u1 should all be included
        List<Message> messages = List.of(
                buildMessage(1L, u1, u2, "Hello"),
                buildMessage(2L, u2, u1, "World")
        );
        Page<Message> page = new PageImpl<>(messages, PageRequest.of(0, 20), 2);
        when(messageRepository.findDialog(eq(1L), eq(2L), any())).thenReturn(page);

        Page<MessageResponse> result = messageService.getDialog(1L, 2L, 0, 20);

        assertThat(result.getContent()).extracting(MessageResponse::getSenderId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getDialog_emptyConversation_returnsEmptyPage() {
        Page<Message> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(messageRepository.findDialog(eq(1L), eq(2L), any())).thenReturn(emptyPage);

        Page<MessageResponse> result = messageService.getDialog(1L, 2L, 0, 20);

        verify(messageRepository).markAsRead(1L, 2L);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }
}
