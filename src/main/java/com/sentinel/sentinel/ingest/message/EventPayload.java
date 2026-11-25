package com.sentinel.sentinel.ingest.message;

import java.time.Instant;
// EventPayload is a sealed interface that represents the payload of an event.
public sealed interface EventPayload permits LoginEvent, FirewallEvent {
    String type();

    Instant timestamp();
}
