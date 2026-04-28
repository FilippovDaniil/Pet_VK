package com.socialnetwork.config;

import com.socialnetwork.event.FriendEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic friendEventsTopic() {
        return TopicBuilder.name("friend-events")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic postEventsTopic() {
        return TopicBuilder.name("post-events")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
