package com.sentinel.sentinel.ingest.message;

import java.time.Instant;
// FirewallEvent is a record that represents a firewall event payload.
public record FirewallEvent(
        String type,
        String sourceIp,
        String destinationIp,
        int destinationPort,
        String action,
        Instant timestamp) implements EventPayload {
}
