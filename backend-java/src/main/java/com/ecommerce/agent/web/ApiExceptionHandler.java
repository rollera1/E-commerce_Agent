package com.ecommerce.agent.web;

import com.ecommerce.agent.copyseo.CopySeoGenerationNotFoundException;
import com.ecommerce.agent.copyseo.CopySeoReviewConflictException;
import com.ecommerce.agent.integration.shopify.ShopifyIntegrationException;
import com.ecommerce.agent.productselection.ProductSelectionConflictException;
import com.ecommerce.agent.productselection.ProductSelectionNotFoundException;
import com.ecommerce.agent.storeconnection.ShopifyNotConfiguredException;
import com.ecommerce.agent.shopifylisting.ListingConflictException;
import com.ecommerce.agent.shopifylisting.ListingNotFoundException;
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

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleListingNotFound(ListingNotFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, "not_found", "shopify-listing",
                "SHOPIFY_LISTING_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ListingConflictException.class)
    public ResponseEntity<Map<String, Object>> handleListingConflict(ListingConflictException exception) {
        return errorResponse(HttpStatus.CONFLICT, "conflict", "shopify-listing",
                "SHOPIFY_LISTING_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(ProductSelectionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductSelectionNotFound(
            ProductSelectionNotFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, "not_found", "product-selection",
                "PRODUCT_SELECTION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ProductSelectionConflictException.class)
    public ResponseEntity<Map<String, Object>> handleProductSelectionConflict(
            ProductSelectionConflictException exception) {
        return errorResponse(HttpStatus.CONFLICT, "conflict", "product-selection",
                "PRODUCT_SELECTION_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(CopySeoGenerationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCopySeoGenerationNotFound(
            CopySeoGenerationNotFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, "not_found", "copy-seo",
                "COPY_SEO_GENERATION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CopySeoReviewConflictException.class)
    public ResponseEntity<Map<String, Object>> handleCopySeoReviewConflict(
            CopySeoReviewConflictException exception) {
        return errorResponse(HttpStatus.CONFLICT, "conflict", "copy-seo",
                "COPY_SEO_REVIEW_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(ShopifyNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleShopifyNotConfigured(
            ShopifyNotConfiguredException exception) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "not_configured", "shopify",
                "SHOPIFY_NOT_CONFIGURED", exception.getMessage());
    }

    @ExceptionHandler(ShopifyIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleShopifyIntegration(
            ShopifyIntegrationException exception) {
        // Shopify 服务不可用返回 503，其他 Shopify 响应错误返回 502。
        HttpStatus status = switch (exception.getCode()) {
            case REMOTE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return errorResponse(status, "error", "shopify",
                exception.getCode().name(), exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus httpStatus, String status, String service, String errorCode, String message) {
        return ResponseEntity.status(httpStatus).body(Map.of(
                "status", status, "service", service, "errorCode", errorCode,
                "message", message, "timestamp", Instant.now().toString()));
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
