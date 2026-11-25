package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.config.KafkaTopicConfig;
import com.sentinel.sentinel.ingest.dto.EventBatch;
import com.sentinel.sentinel.ingest.dto.IngestResponse;
import com.sentinel.sentinel.ingest.dto.Rejection;
import com.sentinel.sentinel.ingest.model.EventMessage;
import com.sentinel.sentinel.ingest.model.EventPayload;
import com.sentinel.sentinel.ingest.model.QuarantineMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngestService {

    private final EventValidator eventValidator;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public IngestService(
            EventValidator eventValidator,
            KafkaTemplate<Object, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.eventValidator = eventValidator;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public IngestResponse ingest(String customerId, EventBatch batch) {
        int accepted = 0;
        int quarantined = 0;
        List<Rejection> rejections = new ArrayList<>();

        for (int index = 0; index < batch.events().size(); index++) {
            var event = batch.events().get(index);
            try {
                EventPayload payload = eventValidator.validate(event);
                publish(KafkaTopicConfig.EVENTS, customerId, new EventMessage(customerId, payload));
                accepted++;
            } catch (EventValidationException exception) {
                publish(
                        KafkaTopicConfig.EVENTS_QUARANTINE,
                        customerId,
                        new QuarantineMessage(customerId, index, event, exception.getMessage()));
                quarantined++;
                rejections.add(new Rejection(index, exception.getMessage()));
            }
        }

        return new IngestResponse(accepted, quarantined, rejections);
    }

    private void publish(String topic, String customerId, Object message) {
        try {
            kafkaTemplate.send(topic, customerId, objectMapper.writeValueAsString(message)).join();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize ingest message", exception);
        }
    }
}
