package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.ingest.message.FirewallEvent;
import com.sentinel.sentinel.ingest.message.LoginEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventValidatorTest {

    private final EventValidator validator = new EventValidator();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void validatesLoginEvents() throws Exception {
        var event = objectMapper.readTree("""
                {"type":"LOGIN","user":"alice","sourceIp":"192.0.2.21",
                 "success":true,"timestamp":"2026-11-23T09:00:00Z"}
                """);

        var result = assertInstanceOf(LoginEvent.class, validator.validate(event));
        assertEquals("alice", result.user());
        assertEquals(Instant.parse("2026-11-23T09:00:00Z"), result.timestamp());
    }

    @Test
    void validatesFirewallEvents() throws Exception {
        var event = objectMapper.readTree("""
                {"type":"FIREWALL","sourceIp":"192.0.2.21","destinationIp":"10.0.0.10",
                 "destinationPort":443,"action":"ALLOW","timestamp":"2026-11-23T09:04:00Z"}
                """);

        var result = assertInstanceOf(FirewallEvent.class, validator.validate(event));
        assertEquals(443, result.destinationPort());
        assertEquals("ALLOW", result.action());
    }

    @Test
    void rejectsUnsupportedEventTypes() throws Exception {
        var event = objectMapper.readTree("""
                {"type":"UNKNOWN","timestamp":"2026-11-23T09:00:00Z"}
                """);

        assertThrows(EventValidationException.class, () -> validator.validate(event));
    }

    @Test
    void rejectsInvalidFirewallPorts() throws Exception {
        var event = objectMapper.readTree("""
                {"type":"FIREWALL","sourceIp":"192.0.2.21","destinationIp":"10.0.0.10",
                 "destinationPort":70000,"action":"ALLOW","timestamp":"2026-11-23T09:04:00Z"}
                """);

        assertThrows(EventValidationException.class, () -> validator.validate(event));
    }

    @Test
    void rejectsMalformedIpAddresses() throws Exception {
        for (String ip : List.of("hello", "example.com", "-1.2.3.4", "+1.2.3.4", "١.2.3.4")) {
            var event = objectMapper.readTree("""
                    {"type":"LOGIN","user":"alice","sourceIp":"%s",
                     "success":true,"timestamp":"2026-11-23T09:00:00Z"}
                    """.formatted(ip));

            assertThrows(EventValidationException.class, () -> validator.validate(event), ip);
        }
    }

    @Test
    void acceptsIpv6Addresses() throws Exception {
        var event = objectMapper.readTree("""
                {"type":"LOGIN","user":"alice","sourceIp":"2001:db8::1",
                 "success":true,"timestamp":"2026-11-23T09:00:00Z"}
                """);

        var result = assertInstanceOf(LoginEvent.class, validator.validate(event));
        assertEquals("2001:db8::1", result.sourceIp());
    }

    @Test
    void rejectsOversizedFieldsWithoutEchoingUnsupportedType() throws Exception {
        var oversizedType = objectMapper.readTree("""
                {"type":"UNKNOWN_UNKNOWN_123","timestamp":"2026-11-23T09:00:00Z"}
                """);
        var unsupportedType = objectMapper.readTree("""
                {"type":"SECRET_TYPE","timestamp":"2026-11-23T09:00:00Z"}
                """);

        var oversizedException = assertThrows(
                EventValidationException.class, () -> validator.validate(oversizedType));
        var unsupportedException = assertThrows(
                EventValidationException.class, () -> validator.validate(unsupportedType));

        assertTrue(oversizedException.getMessage().contains("maximum length"));
        assertEquals("Unsupported event type", unsupportedException.getMessage());
        assertFalse(unsupportedException.getMessage().contains("SECRET_TYPE"));
    }
}
