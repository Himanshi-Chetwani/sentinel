package com.sentinel.sentinel.ingest.dto;
// Index of rejected event in the batch and the reason it was rejected. The index is used to match the rejection to the original event in the batch.
/*
{
  "accepted": 1,
  "quarantined": 5,
  "rejections": [
    { "index": 1, "reason": "Field 'type' must be a non-empty string" },
    { "index": 2, "reason": "Unsupported event type: PRINTER" },
    { "index": 3, "reason": "..." },
    { "index": 4, "reason": "Field 'destinationPort' must be between 1 and 65535" },
    { "index": 5, "reason": "Field 'timestamp' must be an ISO-8601 instant" }
  ]
}
 */
public record Rejection(int index, String reason) {
}
