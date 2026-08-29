package com.ecommerce.agent.web;

import com.ecommerce.agent.integration.shopify.ShopifyIntegrationException;
import com.ecommerce.agent.storeconnection.ShopifyNotConfiguredException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ShopifyNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleShopifyNotConfigured(
            ShopifyNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "not_configured",
                "service", "shopify",
                "errorCode", "SHOPIFY_NOT_CONFIGURED",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(ShopifyIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleShopifyIntegration(
            ShopifyIntegrationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case REMOTE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(Map.of(
                "status", "error",
                "service", "shopify",
                "errorCode", exception.getCode().name(),
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleDownstreamUnavailable(RestClientException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "unavailable",
                "service", "ai-python",
                "message", "Python service is unavailable",
                "timestamp", Instant.now().toString()));
    }
}
