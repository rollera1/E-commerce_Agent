package com.ecommerce.agent.shopifylisting;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VariantPrice(
        @NotBlank String sourceVariantId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price) {
}
