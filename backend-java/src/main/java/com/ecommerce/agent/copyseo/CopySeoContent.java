package com.ecommerce.agent.copyseo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 文案与 SEO Agent 生成、商家可以继续编辑的结构化内容。 */
public record CopySeoContent(
        // 商品标题，最长 255 个字符。
        @NotBlank(message = "商品标题不能为空")
        @Size(max = 255, message = "商品标题不能超过 255 个字符") String title,
        // 商品卖点，要求 1 至 8 条。
        @NotNull(message = "商品卖点不能为空")
        @Size(min = 1, max = 8, message = "商品卖点必须包含 1 至 8 条")
        List<@NotBlank(message = "商品卖点内容不能为空") String> bulletPoints,
        // 商品详细描述。
        @NotBlank(message = "商品描述不能为空") String description,
        // 搜索引擎结果中使用的标题。
        @NotBlank(message = "SEO 标题不能为空")
        @Size(max = 70, message = "SEO 标题不能超过 70 个字符") String seoTitle,
        // 搜索引擎结果中使用的摘要。
        @NotBlank(message = "Meta Description 不能为空")
        @Size(max = 160, message = "Meta Description 不能超过 160 个字符") String metaDescription,
        // 商品和 SEO 标签，要求 1 至 20 个。
        @NotNull(message = "商品标签不能为空")
        @Size(min = 1, max = 20, message = "商品标签必须包含 1 至 20 个")
        List<@NotBlank(message = "商品标签内容不能为空") String> tags) {

    public CopySeoContent {
        // 非空列表复制为不可变列表，防止创建后被外部代码修改。
        bulletPoints = bulletPoints == null ? null : List.copyOf(bulletPoints);
        tags = tags == null ? null : List.copyOf(tags);
    }
}
