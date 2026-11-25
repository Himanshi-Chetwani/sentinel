package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.ingest.message.EventPayload;
import com.sentinel.sentinel.ingest.message.FirewallEvent;
import com.sentinel.sentinel.ingest.message.LoginEvent;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URISyntaxException;
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

        String type = requiredText(event, "type", 16);
        Instant timestamp = timestamp(event);

        return switch (type) {
            case "LOGIN" -> validateLogin(event, timestamp);
            case "FIREWALL" -> validateFirewall(event, timestamp);
            default -> throw new EventValidationException("Unsupported event type");
        };
    }

    // LOGIN: user and sourceIp must be text, success must be a real boolean (true/false, not "true").
    private LoginEvent validateLogin(JsonNode event, Instant timestamp) {
        String user = requiredText(event, "user", 256);
        String sourceIp = ipAddress(event, "sourceIp");
        JsonNode success = event.get("success");
        if (success == null || !success.isBoolean()) {
            throw new EventValidationException("Field 'success' must be a boolean");
        }
        return new LoginEvent("LOGIN", user, sourceIp, success.booleanValue(), timestamp);
    }

    // FIREWALL: sourceIp and destinationIp must be valid IP addresses, destinationPort must be an integer between 1 and 65535,
    // action must be either "ALLOW" or "DENY".
    private FirewallEvent validateFirewall(JsonNode event, Instant timestamp) {
        String sourceIp = ipAddress(event, "sourceIp");
        String destinationIp = ipAddress(event, "destinationIp");
        JsonNode destinationPort = event.get("destinationPort");
        if (destinationPort == null || !destinationPort.isIntegralNumber()
                || !destinationPort.canConvertToInt()
                || destinationPort.intValue() < 1 || destinationPort.intValue() > 65535) {
            throw new EventValidationException("Field 'destinationPort' must be between 1 and 65535");
        }
        String action = requiredText(event, "action", 5);
        if (!action.equals("ALLOW") && !action.equals("DENY")) {
            throw new EventValidationException("Field 'action' must be ALLOW or DENY");
        }
        return new FirewallEvent(
                "FIREWALL", sourceIp, destinationIp, destinationPort.intValue(), action, timestamp);
    }

    // Extracts one text field from the event: it must exist, be a JSON string (not a number,
    // boolean or object) and not be blank. Returns the value used to build the LoginEvent /
    // FirewallEvent; otherwise throws EventValidationException naming the field.
    private String requiredText(JsonNode event, String field, int maxLength) {
        JsonNode value = event.get(field);
        if (value == null || !value.isTextual() || value.stringValue().isBlank()) {
            throw new EventValidationException("Field '" + field + "' must be a non-empty string");
        }
        String text = value.stringValue();
        if (text.length() > maxLength) {
            throw new EventValidationException("Field '" + field + "' exceeds the maximum length");
        }
        return text;
    }

    // Checks an IP field without DNS lookups: IPv4 is parsed by hand (four 0-255 octets, ASCII
    // digits only, no leading zeros); IPv6 uses URI's literal parser. Hostnames are rejected.
    private String ipAddress(JsonNode event, String field) {
        String value = requiredText(event, field, 45);
        if (!isValidIpv4(value) && !isValidIpv6(value)) {
            throw new EventValidationException("Field '" + field + "' must be a valid IP address");
        }
        return value;
    }

    private boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")
                    || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return false;
            }
            int number;
            try {
                number = Integer.parseInt(octet);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (number > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIpv6(String value) {
        if (!value.contains(":") || !value.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        try {
            return new URI("http://[" + value + "]").getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    // Extracts the "timestamp" field and parses it as an ISO-8601 instant (e.g. 2026-11-23T10:00:00Z).
    // Returns the Instant used to build the LoginEvent / FirewallEvent; a missing or unparseable
    // value throws EventValidationException.
    private Instant timestamp(JsonNode event) {
        String value = requiredText(event, "timestamp", 64);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new EventValidationException("Field 'timestamp' must be an ISO-8601 instant");
        }
    }
}
