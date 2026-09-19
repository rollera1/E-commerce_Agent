package com.ecommerce.agent.web;

import com.ecommerce.agent.copyseo.CopySeoGenerationRequest;
import com.ecommerce.agent.copyseo.CopySeoGenerationService;
import com.ecommerce.agent.copyseo.CopySeoGenerationView;
import com.ecommerce.agent.copyseo.CopySeoContent;
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

    public CopySeoGenerationController(CopySeoGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/api/source-products/{sourceProductId}/copy-seo-generations")
    /** 为指定来源商品创建一条文案生成记录。 */
    public CopySeoGenerationView generate(
            @PathVariable String sourceProductId,
            @Valid @RequestBody GenerationBody body) {
        return generationService.generate(new CopySeoGenerationRequest(
                sourceProductId, body.targetMarket(), body.language()));
    }

    @PutMapping("/api/copy-seo-generations/{generationId}/content")
    /** 保存商家对待审核文案的修改。 */
    public CopySeoGenerationView updateContent(
            @PathVariable String generationId,
            @Valid @RequestBody CopySeoContent content) {
        return generationService.updateContent(generationId, content);
    }

    @PostMapping("/api/copy-seo-generations/{generationId}/accept")
    /** 接受生成内容，但本阶段不会自动发布 Shopify 商品。 */
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
            String language) {
    }
}
