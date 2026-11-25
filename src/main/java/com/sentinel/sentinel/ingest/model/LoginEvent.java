package com.sentinel.sentinel.ingest.model;

import java.time.Instant;

public record LoginEvent(
        String type,
        String user,
        String sourceIp,
        boolean success,
        Instant timestamp) implements EventPayload {
}
