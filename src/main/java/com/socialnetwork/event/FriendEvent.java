package com.socialnetwork.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendEvent {

    private String eventId;
    private LocalDateTime timestamp;
    private String type;
    private Long sourceUserId;
    private Long targetUserId;

    public static FriendEvent of(String type, Long sourceUserId, Long targetUserId) {
        return FriendEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type(type)
                .sourceUserId(sourceUserId)
                .targetUserId(targetUserId)
                .build();
    }
}
