package com.ecommerce.agent.web;

import com.ecommerce.agent.copyseo.CopySeoGenerationRequest;
import com.ecommerce.agent.copyseo.CopySeoGenerationService;
import com.ecommerce.agent.copyseo.CopySeoGenerationView;
import com.ecommerce.agent.copyseo.CopySeoContent;
import com.ecommerce.agent.copyseo.GenerationStatus;
import com.ecommerce.agent.productselection.ProductSelectionService;
import com.ecommerce.agent.shopifylisting.ReviewMode;
import com.ecommerce.agent.shopifylisting.ShopifyListingService;
import com.ecommerce.agent.shopifylisting.ShopifyListingSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

/** 文案与 SEO 生成、编辑和人工审核接口。 */
@Validated
@RestController
public class CopySeoGenerationController {
    private final CopySeoGenerationService generationService;
    private final ProductSelectionService selectionService;
    private final ShopifyListingService listingService;

    public CopySeoGenerationController(
            CopySeoGenerationService generationService,
            ProductSelectionService selectionService,
            ShopifyListingService listingService) {
        this.generationService = generationService;
        this.selectionService = selectionService;
        this.listingService = listingService;
    }

    @PostMapping("/api/product-selections/{selectionId}/copy-seo-generations")
    /** 只为商家已经确认的来源商品创建文案生成记录。 */
    public CopySeoGenerationView generate(
            @PathVariable String selectionId,
            @Valid @RequestBody GenerationBody body) {
        String sourceProductId = selectionService.requireConfirmedSourceProduct(selectionId);
        CopySeoGenerationView generated = generationService.generate(new CopySeoGenerationRequest(
                sourceProductId, body.targetMarket(), body.language()));
        if (generated.status() == GenerationStatus.SUCCEEDED && body.listingSettings() != null) {
            listingService.configure(generated.generationId(), sourceProductId, body.listingSettings());
            if (body.listingSettings().reviewMode() == ReviewMode.AUTO) {
                generated = generationService.accept(generated.generationId());
                listingService.processAccepted(generated.generationId());
            }
        }
        return generated;
    }

    @PutMapping("/api/copy-seo-generations/{generationId}/content")
    /** 保存商家对待审核文案的修改。 */
    public CopySeoGenerationView updateContent(
            @PathVariable String generationId,
            @Valid @RequestBody CopySeoContent content) {
        return generationService.updateContent(generationId, content);
    }

    @PostMapping("/api/copy-seo-generations/{generationId}/accept")
    /** 仅接受内容；草稿创建由独立且显式的操作触发。 */
    public CopySeoGenerationView accept(@PathVariable String generationId) {
        return generationService.accept(generationId);
    }

    @PostMapping("/api/copy-seo-generations/{generationId}/reject")
    /** 拒绝生成内容。 */
    public CopySeoGenerationView reject(@PathVariable String generationId) {
        return generationService.reject(generationId);
    }

    /** 前端发起生成时提交的市场和语言。 */
    public record GenerationBody(
            @NotBlank(message = "目标市场不能为空")
            @Pattern(regexp = "^[A-Z]{2}$", message = "目标市场必须是两位大写代码，例如 US")
            String targetMarket,
            @NotBlank(message = "目标语言不能为空")
            @Pattern(regexp = "^[a-z]{2}$", message = "目标语言必须是两位小写代码，例如 en")
            String language,
            @Valid ShopifyListingSettings listingSettings) {
    }
}
