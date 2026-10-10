package com.ecommerce.agent.web;

import com.ecommerce.agent.productselection.ProductSelectionConfirmationView;
import com.ecommerce.agent.productselection.ProductSelectionSearchRequest;
import com.ecommerce.agent.productselection.ProductSelectionSearchView;
import com.ecommerce.agent.productselection.ProductSelectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品检索和商家选品确认接口。 */
@Validated
@RestController
@RequestMapping("/api/product-selections")
public class ProductSelectionController {
    private final ProductSelectionService selectionService;

    public ProductSelectionController(ProductSelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @PostMapping("/searches")
    public ProductSelectionSearchView search(
            @Valid @RequestBody ProductSelectionSearchRequest request) {
        return selectionService.search(request);
    }

    @PostMapping("/{selectionId}/confirm")
    public ProductSelectionConfirmationView confirm(
            @PathVariable String selectionId,
            @Valid @RequestBody ConfirmBody body) {
        return selectionService.confirm(selectionId, body.sourceProductId());
    }

    /** 商家确认候选商品时提交的最小请求。 */
    public record ConfirmBody(@NotBlank String sourceProductId) {
    }
}
