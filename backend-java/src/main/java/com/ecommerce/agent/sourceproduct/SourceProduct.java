package com.ecommerce.agent.sourceproduct;

import java.time.Instant;
import java.util.List;

/** 从 CJ 或其他商品来源转换得到的统一商品结构。 */
public record SourceProduct(
        // 商品来源名称，例如 CJ 或 FAKE。
        String source,
        // 商品在来源平台中的 ID。
        String sourceProductId,
        // 来源商品标题。
        String title,
        // 来源商品描述。
        String description,
        // 商品图片地址列表。
        List<String> images,
        // 商品起售价和币种。
        Money price,
        // 商品变体列表。
        List<SourceVariant> variants,
        // 用于追溯来源的商品地址。
        String sourceUrl,
        // 本次数据抓取时间。
        Instant fetchedAt,
        Integer inventoryTotal) {
    public SourceProduct(String source, String sourceProductId, String title,
            String description, List<String> images, Money price, List<SourceVariant> variants,
            String sourceUrl, Instant fetchedAt) {
        this(source, sourceProductId, title, description, images, price, variants,
                sourceUrl, fetchedAt, null);
    }
}
