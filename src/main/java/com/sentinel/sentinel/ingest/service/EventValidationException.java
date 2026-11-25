package com.sentinel.sentinel.ingest.service;

public class EventValidationException extends RuntimeException {
    // Exception thrown when an event is rejected during validation.
    // The message is used as the reason in the Rejection object.
    public EventValidationException(String message) {
        super(message);
    }
}
