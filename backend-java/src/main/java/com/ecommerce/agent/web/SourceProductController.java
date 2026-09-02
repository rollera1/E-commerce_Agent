package com.ecommerce.agent.web;

import com.ecommerce.agent.sourceproduct.SourceProduct;
import com.ecommerce.agent.sourceproduct.SourceProductPage;
import com.ecommerce.agent.sourceproduct.SourceProductQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/source-products")
public class SourceProductController {

    private final SourceProductQueryService queryService;

    public SourceProductController(SourceProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public SourceProductPage list(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return queryService.list(keyword, page, pageSize);
    }

    @GetMapping("/{sourceProductId}")
    public SourceProduct get(@PathVariable String sourceProductId) {
        return queryService.get(sourceProductId);
    }
}
