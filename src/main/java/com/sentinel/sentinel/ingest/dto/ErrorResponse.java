package com.sentinel.sentinel.ingest.dto;
// Response body for POST /api/v1/events when the request body is rejected before the controller runs.
public record ErrorResponse(String error, String message) {
}
