package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.config.KafkaTopicConfig;
import com.sentinel.sentinel.ingest.dto.EventBatch;
import com.sentinel.sentinel.ingest.dto.IngestResponse;
import com.sentinel.sentinel.ingest.dto.Rejection;
import com.sentinel.sentinel.ingest.message.EventMessage;
import com.sentinel.sentinel.ingest.message.EventPayload;
import com.sentinel.sentinel.ingest.message.QuarantineMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class IngestService {

    private final EventValidator eventValidator;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Injecting the EventValidator, KafkaTemplate, and ObjectMapper into the IngestService class.
    // The EventValidator is used to validate incoming events,
    // the KafkaTemplate is used to send messages to Kafka topics,
    // and the ObjectMapper is used to serialize objects to JSON.
    public IngestService(
            EventValidator eventValidator,
            KafkaTemplate<Object, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.eventValidator = eventValidator;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    // Processes one batch for one customer (customerId comes from the verified token, never the request).
    // Each event is validated on its own:
    //   - valid   -> published to the "events" topic as an EventMessage, keyed by customerId
    //   - invalid -> published to "events-quarantine" with the raw event and the reason,
    //                and recorded as a Rejection so the sender knows which event failed and why.
    // A bad event never stops the batch: the remaining events are still processed.
    // Returns the counts and rejections for the HTTP response; Kafka is for sentinel, the response is for the sender.
    // rejections is a list of events that were quarentied iwth their reasons
    public IngestResponse ingest(String customerId, EventBatch batch) {
        List<Rejection> rejections = IntStream.range(0, batch.events().size())
                .mapToObj(index -> {
                    var event = batch.events().get(index);
                    try {
                        EventPayload payload = eventValidator.validate(event);
                        publish(KafkaTopicConfig.EVENTS, customerId, new EventMessage(customerId, payload));
                        return Optional.<Rejection>empty();
                    } catch (EventValidationException exception) {
                        publish(
                                KafkaTopicConfig.EVENTS_QUARANTINE,
                                customerId,
                                new QuarantineMessage(customerId, index, event, exception.getMessage()));
                        return Optional.of(new Rejection(index, exception.getMessage()));
                    }
                })
                .flatMap(Optional::stream)
                .toList();

        int quarantined = rejections.size();
        int accepted = batch.events().size() - quarantined;
        return new IngestResponse(accepted, quarantined, rejections);
    }

    // Sends one message to Kafka as JSON text, keyed by customerId so each customer's events stay in order.
    // join() waits for Kafka to confirm the write; if Kafka is down the request fails (500).
    private void publish(String topic, String customerId, Object message) {
        try {
            kafkaTemplate.send(topic, customerId, objectMapper.writeValueAsString(message)).join();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize ingest message", exception);
        }
    }
}
