package com.sentinel.sentinel.ingest.model;

public record EventMessage(String customerId, EventPayload event) {
}
