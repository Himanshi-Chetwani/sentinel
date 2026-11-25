package com.sentinel.sentinel.ingest.dto;

import java.util.List;
// Response body for POST /api/v1/events.
// Contains the counts of accepted and quarantined events, and a list of rejections with their reasons.
public record IngestResponse(int accepted, int quarantined, List<Rejection> rejections) {
}
