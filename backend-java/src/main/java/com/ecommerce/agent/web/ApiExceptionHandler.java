package com.ecommerce.agent.web;

import com.ecommerce.agent.copyseo.CopySeoGenerationNotFoundException;
import com.ecommerce.agent.copyseo.CopySeoReviewConflictException;
import com.ecommerce.agent.integration.shopify.ShopifyIntegrationException;
import com.ecommerce.agent.storeconnection.ShopifyNotConfiguredException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/** 将业务异常和外部服务异常统一转换为前端可识别的 HTTP 响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CopySeoGenerationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCopySeoGenerationNotFound(
            CopySeoGenerationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", "not_found",
                "service", "copy-seo",
                "errorCode", "COPY_SEO_GENERATION_NOT_FOUND",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(CopySeoReviewConflictException.class)
    public ResponseEntity<Map<String, Object>> handleCopySeoReviewConflict(
            CopySeoReviewConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", "conflict",
                "service", "copy-seo",
                "errorCode", "COPY_SEO_REVIEW_CONFLICT",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()));
    }

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
        // Shopify 服务不可用返回 503，其他 Shopify 响应错误返回 502。
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
        // 未被更具体异常处理器捕获的 RestClient 错误视为 Python 服务不可用。
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "unavailable",
                "service", "ai-python",
                "message", "Python 服务当前不可用",
                "timestamp", Instant.now().toString()));
    }
}
