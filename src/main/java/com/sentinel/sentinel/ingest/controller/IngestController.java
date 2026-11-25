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

    // Controller gets the request from the client, in our case the script that runs on the customer's machine.
    // It validates the request body and passes it to the service layer.
    // Here it only validates the request body i.e 1000 event size and no extra fields, but the service layer will validate each event in the batch.
    // It gets the current customer from the JWT token, which is validated by Spring Security before this method is called.
    @PostMapping
    public IngestResponse ingest(
            @Valid @RequestBody EventBatch batch,
            @AuthenticationPrincipal Jwt jwt) {
            return ingestService.ingest(CurrentCustomer.from(jwt), batch);
    }
}
