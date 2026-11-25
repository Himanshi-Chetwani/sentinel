package com.sentinel.sentinel.ingest.message;

import java.time.Instant;
// LoginEvent is a record that represents a login event payload.
public record LoginEvent(
        String type,
        String user,
        String sourceIp,
        boolean success,
        Instant timestamp) implements EventPayload {
}
