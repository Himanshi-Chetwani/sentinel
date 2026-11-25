package com.sentinel.sentinel.ingest.service;

import com.sentinel.sentinel.ingest.message.FirewallEvent;
import com.sentinel.sentinel.ingest.message.LoginEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
