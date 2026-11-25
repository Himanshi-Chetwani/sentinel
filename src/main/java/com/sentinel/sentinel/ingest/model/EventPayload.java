package com.sentinel.sentinel.ingest.model;

import java.time.Instant;

public sealed interface EventPayload permits LoginEvent, FirewallEvent {
    String type();

    Instant timestamp();
}
