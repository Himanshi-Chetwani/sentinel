package com.sentinel.sentinel.ingest.message;

import tools.jackson.databind.JsonNode;
// QuarantineMessage is a record that represents a quarantined event payload.
public record QuarantineMessage(String customerId, int index, JsonNode event, String reason) {
}
