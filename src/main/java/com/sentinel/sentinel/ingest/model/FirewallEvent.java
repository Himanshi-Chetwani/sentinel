package com.sentinel.sentinel.ingest.model;

import java.time.Instant;

public record FirewallEvent(
        String type,
        String sourceIp,
        String destinationIp,
        int destinationPort,
        String action,
        Instant timestamp) implements EventPayload {
}
