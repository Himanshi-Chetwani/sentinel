package com.sentinel.sentinel.ingest.controller;

import com.sentinel.sentinel.ingest.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
// Spring calls this automatically when a request body is rejected before the controller runs:
//  - HttpMessageNotReadableException: not valid JSON, "events" not a list, or an unknown top-level field
//  - MethodArgumentNotValidException: @Valid failed (events empty/missing or more than 1000)
// Both become 400 with a generic message, so no internal details (stack traces, class names) leak.
// Other errors are handled elsewhere: bad events -> quarantine (IngestService), 401/403 -> Spring Security.@RestControllerAdvice
public class IngestExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request",
                "Body must be JSON with an 'events' list and no other top-level fields"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBatch(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_batch",
                "events must contain between 1 and 1000 items"));
    }
}
