package com.ecommerce.agent.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.agent.copyseo.CopySeoGenerationNotFoundException;
import com.ecommerce.agent.copyseo.CopySeoReviewConflictException;
import com.ecommerce.agent.integration.shopify.ShopifyErrorCode;
import com.ecommerce.agent.integration.shopify.ShopifyIntegrationException;
import com.ecommerce.agent.productselection.ProductSelectionConflictException;
import com.ecommerce.agent.productselection.ProductSelectionNotFoundException;
import com.ecommerce.agent.shopifylisting.ListingConflictException;
import com.ecommerce.agent.shopifylisting.ListingNotFoundException;
import com.ecommerce.agent.storeconnection.ShopifyNotConfiguredException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

class ApiExceptionHandlerTest {
    private static final ApiExceptionHandler HANDLER = new ApiExceptionHandler();

    static Stream<Arguments> businessErrors() {
        return Stream.of(
                Arguments.of(HANDLER.handleListingNotFound(new ListingNotFoundException("id")),
                        404, "not_found", "shopify-listing", "SHOPIFY_LISTING_NOT_FOUND", "Shopify 上架任务不存在：id"),
                Arguments.of(HANDLER.handleListingConflict(new ListingConflictException("冲突")),
                        409, "conflict", "shopify-listing", "SHOPIFY_LISTING_CONFLICT", "冲突"),
                Arguments.of(HANDLER.handleProductSelectionNotFound(new ProductSelectionNotFoundException("id")),
                        404, "not_found", "product-selection", "PRODUCT_SELECTION_NOT_FOUND", "选品记录不存在：id"),
                Arguments.of(HANDLER.handleProductSelectionConflict(new ProductSelectionConflictException("冲突")),
                        409, "conflict", "product-selection", "PRODUCT_SELECTION_CONFLICT", "冲突"),
                Arguments.of(HANDLER.handleCopySeoGenerationNotFound(new CopySeoGenerationNotFoundException("id")),
                        404, "not_found", "copy-seo", "COPY_SEO_GENERATION_NOT_FOUND", "未找到文案与 SEO 生成记录：id"),
                Arguments.of(HANDLER.handleCopySeoReviewConflict(new CopySeoReviewConflictException("冲突")),
                        409, "conflict", "copy-seo", "COPY_SEO_REVIEW_CONFLICT", "冲突"),
                Arguments.of(HANDLER.handleShopifyNotConfigured(new ShopifyNotConfiguredException("未配置")),
                        503, "not_configured", "shopify", "SHOPIFY_NOT_CONFIGURED", "未配置"));
    }

    @ParameterizedTest
    @MethodSource("businessErrors")
    void preservesBusinessErrorContract(ResponseEntity<Map<String, Object>> response,
            int httpStatus, String status, String service, String code, String message) {
        assertResponse(response, httpStatus, status, service, code, message);
    }

    @ParameterizedTest
    @EnumSource(ShopifyErrorCode.class)
    void preservesShopifyErrorClassification(ShopifyErrorCode code) {
        var response = HANDLER.handleShopifyIntegration(new ShopifyIntegrationException(code, "远端错误"));
        assertResponse(response, code == ShopifyErrorCode.REMOTE_UNAVAILABLE ? 503 : 502,
                "error", "shopify", code.name(), "远端错误");
    }

    @Test
    void keepsDownstreamErrorSanitizedAndWithoutErrorCode() {
        var response = HANDLER.handleDownstreamUnavailable(new RestClientException("private-details"));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("message", "Python 服务当前不可用")
                .doesNotContainKey("errorCode");
        assertThat(response.getBody().toString()).doesNotContain("private-details");
    }

    private static void assertResponse(ResponseEntity<Map<String, Object>> response,
            int httpStatus, String status, String service, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(httpStatus);
        assertThat(response.getBody()).containsOnlyKeys("status", "service", "errorCode", "message", "timestamp")
                .containsEntry("status", status).containsEntry("service", service)
                .containsEntry("errorCode", code).containsEntry("message", message);
        assertThat(Instant.parse((String) response.getBody().get("timestamp"))).isNotNull();
    }
}
