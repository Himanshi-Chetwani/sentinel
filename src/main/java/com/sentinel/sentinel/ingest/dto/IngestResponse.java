package com.sentinel.sentinel.ingest.dto;

import java.util.List;

public record IngestResponse(int accepted, int quarantined, List<Rejection> rejections) {
}
