package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.ingest.message.EventPayload;
import com.sentinel.sentinel.ingest.message.FirewallEvent;
import com.sentinel.sentinel.ingest.message.LoginEvent;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

@Component
// Validates one raw event from a batch.
// Returns a typed LoginEvent or FirewallEvent if every check for its type passes;
// otherwise throws EventValidationException with the reason, and IngestService quarantines that event.
public class EventValidator {

    // Base checks first (JSON object, type, timestamp), then the checks for the event's type.
    // The returned object holds only the known fields, so anything extra in the JSON is dropped.
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

    // LOGIN: user and sourceIp must be text, success must be a real boolean (true/false, not "true").
    private LoginEvent validateLogin(JsonNode event, Instant timestamp) {
        String user = requiredText(event, "user");
        String sourceIp = requiredText(event, "sourceIp");
        JsonNode success = event.get("success");
        if (success == null || !success.isBoolean()) {
            throw new EventValidationException("Field 'success' must be a boolean");
        }
        return new LoginEvent("LOGIN", user, sourceIp, success.booleanValue(), timestamp);
    }

    // FIREWALL: sourceIp and destinationIp must be text, destinationPort must be an integer between 1 and 65535,
    // action must be either "ALLOW" or "DENY".
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

    // Extracts one text field from the event: it must exist, be a JSON string (not a number,
    // boolean or object) and not be blank. Returns the value used to build the LoginEvent /
    // FirewallEvent; otherwise throws EventValidationException naming the field.
    private String requiredText(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || !value.isTextual() || value.stringValue().isBlank()) {
            throw new EventValidationException("Field '" + field + "' must be a non-empty string");
        }
        return value.stringValue();
    }

    // Extracts the "timestamp" field and parses it as an ISO-8601 instant (e.g. 2026-11-23T10:00:00Z).
    // Returns the Instant used to build the LoginEvent / FirewallEvent; a missing or unparseable
    // value throws EventValidationException.
    private Instant timestamp(JsonNode event) {
        String value = requiredText(event, "timestamp");
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new EventValidationException("Field 'timestamp' must be an ISO-8601 instant");
        }
    }
}
