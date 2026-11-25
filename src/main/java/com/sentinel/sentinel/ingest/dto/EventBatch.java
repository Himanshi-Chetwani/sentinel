package com.sentinel.sentinel.ingest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EventBatch(@NotEmpty List<JsonNode> events) {
}
