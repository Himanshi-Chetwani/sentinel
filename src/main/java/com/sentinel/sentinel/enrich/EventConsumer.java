package com.sentinel.sentinel.enrich;

import com.sentinel.sentinel.config.KafkaTopicConfig;
import com.sentinel.sentinel.ingest.message.EventPayload;
import com.sentinel.sentinel.ingest.message.FirewallEvent;
import com.sentinel.sentinel.ingest.message.LoginEvent;
import com.sentinel.sentinel.ingest.service.EventValidationException;
import com.sentinel.sentinel.ingest.service.EventValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final BadIpList badIpList;
    private final EventRepository eventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public EventConsumer(
            ObjectMapper objectMapper,
            EventValidator eventValidator,
            BadIpList badIpList,
            EventRepository eventRepository,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.eventValidator = eventValidator;
        this.badIpList = badIpList;
        this.eventRepository = eventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaTopicConfig.EVENTS)
    public void consume(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JacksonException exception) {
            quarantine(message, "Unreadable message on events topic", null);
            return;
        }

        if (root == null || !root.isObject()) {
            quarantine(message, "Message must be a JSON object", null);
            return;
        }

        JsonNode customerNode = root.get("customerId");
        if (customerNode == null || !customerNode.isTextual() || customerNode.stringValue().isBlank()) {
            quarantine(message, "Field 'customerId' must be a non-empty string", null);
            return;
        }
        String customerId = customerNode.stringValue();

        EventPayload payload;
        try {
            payload = eventValidator.validate(root.get("event"));
        } catch (EventValidationException exception) {
            quarantine(message, exception.getMessage(), customerId);
            return;
        }

        StoredEvent storedEvent;
        if (payload instanceof LoginEvent loginEvent) {
            boolean knownBad = badIpList.contains(loginEvent.sourceIp());
            storedEvent = StoredEvent.fromLogin(customerId, loginEvent, knownBad);
        } else if (payload instanceof FirewallEvent firewallEvent) {
            boolean knownBad = badIpList.contains(firewallEvent.sourceIp());
            storedEvent = StoredEvent.fromFirewall(customerId, firewallEvent, knownBad);
        } else {
            throw new IllegalStateException("Unsupported event payload type: " + payload.getClass().getName());
        }

        eventRepository.save(storedEvent);
    }

    private void quarantine(String rawMessage, String reason, String customerId) {
        try {
            String quarantineMessage = objectMapper.writeValueAsString(new ConsumerQuarantineMessage(rawMessage, reason));
            kafkaTemplate.send(KafkaTopicConfig.EVENTS_QUARANTINE, customerId, quarantineMessage).join();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize consumer quarantine message", exception);
        }
    }

    private record ConsumerQuarantineMessage(String rawMessage, String reason) {
    }
}
