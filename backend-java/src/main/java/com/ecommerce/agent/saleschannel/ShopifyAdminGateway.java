package com.ecommerce.agent.saleschannel;

import java.util.List;

/** Java 业务层访问 Shopify Admin 能力的接口。 */
public interface ShopifyAdminGateway {

    // 读取并验证指定 Shopify 店铺的基本信息。
    ShopifyShopInfo fetchShopInfo(ShopifyStoreDomain storeDomain);

    List<ShopifyLocation> fetchLocations(ShopifyStoreDomain storeDomain);

    List<ShopifyPublication> fetchPublications(ShopifyStoreDomain storeDomain);

    ShopifyDraftProduct createDraft(
            ShopifyStoreDomain storeDomain,
            ShopifyProductCommand command);

    List<ShopifyCreatedVariant> createVariants(
            ShopifyStoreDomain storeDomain,
            String productId,
            String locationId,
            List<ShopifyVariantCommand> variants);

    void updateDraft(
            ShopifyStoreDomain storeDomain,
            String productId,
            ShopifyProductCommand command,
            List<ShopifyVariantUpdate> variants);

    void replaceProductMedia(
            ShopifyStoreDomain storeDomain,
            String productId,
            String altText,
            List<String> imageUrls);

    boolean mediaReady(ShopifyStoreDomain storeDomain, String productId);

    void activateProduct(ShopifyStoreDomain storeDomain, String productId);

    void publishProduct(
            ShopifyStoreDomain storeDomain,
            String productId,
            String publicationId);
}
