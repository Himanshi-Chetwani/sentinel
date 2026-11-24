package com.sentinel.sentinel.ingest;

import java.util.List;

public record IngestResponse(int accepted, int quarantined, List<Rejection> rejections) {
}
