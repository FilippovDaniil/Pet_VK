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

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messages")
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message")
    public MessageResponse sendMessage(@AuthenticationPrincipal UserDetails userDetails,
                                       @Valid @RequestBody MessageRequest request) {
        Long senderId = userService.getUserByEmail(userDetails.getUsername()).getId();
        return messageService.sendMessage(senderId, request);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get dialog with user (paginated, marks as read)")
    public Page<MessageResponse> getDialog(@AuthenticationPrincipal UserDetails userDetails,
                                            @PathVariable Long userId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Long currentId = userService.getUserByEmail(userDetails.getUsername()).getId();
        return messageService.getDialog(currentId, userId, page, size);
    }
}
