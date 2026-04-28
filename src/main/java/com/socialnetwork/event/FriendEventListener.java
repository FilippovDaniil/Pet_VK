package com.socialnetwork.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FriendEventListener {

    @KafkaListener(topics = "friend-events", groupId = "social-network-group")
    public void handleFriendEvent(FriendEvent event) {
        log.info("[NOTIFICATION] Friend event received: type={}, from user {} to user {}",
                event.getType(), event.getSourceUserId(), event.getTargetUserId());
    }
}
