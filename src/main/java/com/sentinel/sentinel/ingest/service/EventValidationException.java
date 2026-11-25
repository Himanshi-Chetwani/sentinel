package com.sentinel.sentinel.ingest.service;

public class EventValidationException extends RuntimeException {

    public EventValidationException(String message) {
        super(message);
    }
}
