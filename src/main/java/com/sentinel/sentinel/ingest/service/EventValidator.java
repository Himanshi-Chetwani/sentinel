package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.ingest.model.EventPayload;
import com.sentinel.sentinel.ingest.model.FirewallEvent;
import com.sentinel.sentinel.ingest.model.LoginEvent;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

@Component
public class EventValidator {

    public EventPayload validate(JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new EventValidationException("Event must be a JSON object");
        }

        String type = requiredText(event, "type");
        Instant timestamp = timestamp(event);

        return switch (type) {
            case "LOGIN" -> validateLogin(event, timestamp);
            case "FIREWALL" -> validateFirewall(event, timestamp);
            default -> throw new EventValidationException("Unsupported event type: " + type);
        };
    }

    private LoginEvent validateLogin(JsonNode event, Instant timestamp) {
        String user = requiredText(event, "user");
        String sourceIp = requiredText(event, "sourceIp");
        JsonNode success = event.get("success");
        if (success == null || !success.isBoolean()) {
            throw new EventValidationException("Field 'success' must be a boolean");
        }
        return new LoginEvent("LOGIN", user, sourceIp, success.booleanValue(), timestamp);
    }

    private FirewallEvent validateFirewall(JsonNode event, Instant timestamp) {
        String sourceIp = requiredText(event, "sourceIp");
        String destinationIp = requiredText(event, "destinationIp");
        JsonNode destinationPort = event.get("destinationPort");
        if (destinationPort == null || !destinationPort.isIntegralNumber()
                || !destinationPort.canConvertToInt()
                || destinationPort.intValue() < 1 || destinationPort.intValue() > 65535) {
            throw new EventValidationException("Field 'destinationPort' must be between 1 and 65535");
        }
        String action = requiredText(event, "action");
        if (!action.equals("ALLOW") && !action.equals("DENY")) {
            throw new EventValidationException("Field 'action' must be ALLOW or DENY");
        }
        return new FirewallEvent(
                "FIREWALL", sourceIp, destinationIp, destinationPort.intValue(), action, timestamp);
    }

    private String requiredText(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || !value.isTextual() || value.stringValue().isBlank()) {
            throw new EventValidationException("Field '" + field + "' must be a non-empty string");
        }
        return value.stringValue();
    }

    private Instant timestamp(JsonNode event) {
        String value = requiredText(event, "timestamp");
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new EventValidationException("Field 'timestamp' must be an ISO-8601 instant");
        }
    }
}
