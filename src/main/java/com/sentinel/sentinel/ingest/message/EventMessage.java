package com.sentinel.sentinel.ingest.message;
// EventMessage is a record that represents an event message containing a customer ID and an event payload.
public record EventMessage(String customerId, EventPayload event) {
}
