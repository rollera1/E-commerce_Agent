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

/** 来源商品列表、搜索和详情接口。 */
@Validated
@RestController
@RequestMapping("/api/source-products")
public class SourceProductController {

    private final SourceProductQueryService queryService;

    public SourceProductController(SourceProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    /** 分页查询来源商品，keyword 为空时返回普通列表。 */
    public SourceProductPage list(
            @RequestParam(required = false)
            @Size(max = 100, message = "搜索关键词不能超过 100 个字符") String keyword,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码不能小于 1") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页数量不能小于 1")
            @Max(value = 100, message = "每页数量不能超过 100") int pageSize) {
        return queryService.list(keyword, page, pageSize);
    }

    @GetMapping("/{sourceProductId}")
    public SourceProduct get(@PathVariable String sourceProductId) {
        return queryService.get(sourceProductId);
    }
}
