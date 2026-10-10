package com.ecommerce.agent.productselection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 商家发起关键词选品时提交的请求。 */
public record ProductSelectionSearchRequest(
        @NotBlank @Size(max = 100) String query,
        @Min(1) @Max(100) Integer limit) {

    public int resolvedLimit() {
        return limit == null ? 10 : limit;
    }
}
