package com.ecommerce.agent.storeconnection;

import com.ecommerce.agent.saleschannel.ShopifyShopInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "shopify_store_connection")
public class ShopifyStoreConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shopify_shop_id", nullable = false, unique = true, length = 100)
    private String shopifyShopId;

    @Column(name = "myshopify_domain", nullable = false, unique = true)
    private String myshopifyDomain;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "primary_domain")
    private String primaryDomain;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false, length = 32)
    private ShopifyAuthorizationStatus authorizationStatus;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopifyStoreConnection() {
    }

    private ShopifyStoreConnection(ShopifyShopInfo shop, Instant now) {
        this.connectedAt = now;
        this.createdAt = now;
        refresh(shop, now);
    }

    public static ShopifyStoreConnection connected(ShopifyShopInfo shop, Instant now) {
        return new ShopifyStoreConnection(shop, now);
    }

    public void refresh(ShopifyShopInfo shop, Instant now) {
        this.shopifyShopId = shop.shopifyId();
        this.myshopifyDomain = shop.myshopifyDomain();
        this.shopName = shop.name();
        this.primaryDomain = shop.primaryDomain();
        this.currencyCode = shop.currencyCode();
        this.authorizationStatus = ShopifyAuthorizationStatus.CONNECTED;
        this.lastVerifiedAt = now;
        this.updatedAt = now;
    }

    public ShopifyStoreConnectionView toView() {
        return new ShopifyStoreConnectionView(
                shopifyShopId,
                myshopifyDomain,
                shopName,
                primaryDomain,
                currencyCode,
                authorizationStatus,
                connectedAt,
                lastVerifiedAt);
    }
}
