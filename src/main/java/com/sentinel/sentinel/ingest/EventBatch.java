package com.sentinel.sentinel.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EventBatch(List<JsonNode> events) {
}
