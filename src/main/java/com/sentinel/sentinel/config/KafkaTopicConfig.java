package com.sentinel.sentinel.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    public static final String EVENTS = "events";
    public static final String EVENTS_QUARANTINE = "events-quarantine";
    @Bean
    public NewTopic eventsTopic(){
        return TopicBuilder.name(EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic eventsQuarantineTopic(){
        return TopicBuilder.name(EVENTS_QUARANTINE)
                .partitions(3)
                .replicas(1)
                .build();
    }

}
