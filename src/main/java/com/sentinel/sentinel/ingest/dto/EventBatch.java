package com.sentinel.sentinel.ingest.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

// Request body for POST /api/v1/events.
// Events are kept as raw JSON on purpose: EventValidator checks each one separately, so a
// malformed event is quarantined instead of failing the whole batch. Binding straight to typed
// event objects would make one bad event reject the entire request.
// Unknown top-level fields are rejected by spring.jackson.deserialization.fail-on-unknown-properties.
// Fields inside events are checked by EventValidator and unknown event fields are discarded.
// @NotEmpty / @Size: 1 to 1000 events per batch, otherwise 400 Bad Request (checked via @Valid
// in IngestController). 401/403 are handled earlier, by SecurityConfig.
public record EventBatch(@NotEmpty @Size(max = 1000) List<JsonNode> events) {
}