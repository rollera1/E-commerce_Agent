package com.ecommerce.agent.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleDownstreamUnavailable(RestClientException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "unavailable",
                "service", "ai-python",
                "message", "Python service is unavailable",
                "timestamp", Instant.now().toString()));
    }
}
