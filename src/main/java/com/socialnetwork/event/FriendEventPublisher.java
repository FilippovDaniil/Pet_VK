package com.socialnetwork.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendEventPublisher {

    private static final String TOPIC = "friend-events";

    private final KafkaTemplate<String, FriendEvent> kafkaTemplate;

    public void publish(FriendEvent event) {
        kafkaTemplate.send(TOPIC, event.getEventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish friend event: {}", ex.getMessage());
                    } else {
                        log.debug("Published friend event: type={}, source={}, target={}",
                                event.getType(), event.getSourceUserId(), event.getTargetUserId());
                    }
                });
    }
}
