package com.ecommerce.agent.shopifylisting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "shopify_listing_variant")
public class ShopifyListingVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "generation_id", nullable = false, length = 36)
    private String generationId;
    @Column(name = "source_variant_id", nullable = false, length = 200)
    private String sourceVariantId;
    @Column(name = "option_value", nullable = false)
    private String optionValue;
    @Column(nullable = false)
    private String sku;
    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;
    @Column(name = "source_inventory", nullable = false)
    private int sourceInventory;
    @Column(name = "shopify_variant_id", length = 100)
    private String shopifyVariantId;
    @Column(name = "shopify_inventory_item_id", length = 100)
    private String shopifyInventoryItemId;

    protected ShopifyListingVariant() {}

    public static ShopifyListingVariant configured(
            String generationId, String sourceVariantId, String optionValue,
            String sku, BigDecimal price, int inventory) {
        var value = new ShopifyListingVariant();
        value.generationId = generationId;
        value.sourceVariantId = sourceVariantId;
        value.optionValue = optionValue;
        value.sku = sku;
        value.sellingPrice = price;
        value.sourceInventory = inventory;
        return value;
    }

    public void bindShopify(String variantId, String inventoryItemId) {
        this.shopifyVariantId = variantId;
        this.shopifyInventoryItemId = inventoryItemId;
    }

    public void updatePrice(BigDecimal price) { this.sellingPrice = price; }
    public String sourceVariantId() { return sourceVariantId; }
    public String optionValue() { return optionValue; }
    public String sku() { return sku; }
    public BigDecimal sellingPrice() { return sellingPrice; }
    public int sourceInventory() { return sourceInventory; }
    public String shopifyVariantId() { return shopifyVariantId; }
    public String shopifyInventoryItemId() { return shopifyInventoryItemId; }
}
