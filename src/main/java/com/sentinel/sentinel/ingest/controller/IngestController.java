package com.sentinel.sentinel.ingest.controller;

import com.sentinel.sentinel.ingest.dto.EventBatch;
import com.sentinel.sentinel.ingest.dto.IngestResponse;
import com.sentinel.sentinel.ingest.service.IngestService;
import com.sentinel.sentinel.security.CurrentCustomer;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public IngestResponse ingest(
            @Valid @RequestBody EventBatch batch,
            @AuthenticationPrincipal Jwt jwt) {
        return ingestService.ingest(CurrentCustomer.from(jwt), batch);
    }
}
