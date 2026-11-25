package com.sentinel.sentinel.ingest.model;

import tools.jackson.databind.JsonNode;

public record QuarantineMessage(String customerId, int index, JsonNode event, String reason) {
}
