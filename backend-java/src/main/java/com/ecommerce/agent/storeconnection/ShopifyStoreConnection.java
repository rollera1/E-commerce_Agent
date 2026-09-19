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

/** 已验证 Shopify 店铺连接实体，对应表 shopify_store_connection。 */
@Entity
@Table(name = "shopify_store_connection")
public class ShopifyStoreConnection {

    // 本地数据库自增主键。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Shopify 返回的全局店铺 ID。
    @Column(name = "shopify_shop_id", nullable = false, unique = true, length = 100)
    private String shopifyShopId;

    // Shopify 永久店铺域名，同时用于保证单店记录唯一。
    @Column(name = "myshopify_domain", nullable = false, unique = true)
    private String myshopifyDomain;

    // 店铺显示名称。
    @Column(name = "shop_name", nullable = false)
    private String shopName;

    // 商家当前设置的主域名。
    @Column(name = "primary_domain")
    private String primaryDomain;

    // 店铺三位币种代码。
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    // 当前授权连接状态。
    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false, length = 32)
    private ShopifyAuthorizationStatus authorizationStatus;

    // 首次成功连接时间。
    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    // 最近一次调用 Shopify 验证成功的时间。
    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    // 本地记录创建时间。
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 本地记录最后更新时间。
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopifyStoreConnection() {
        // JPA 加载实体时使用。
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
        // 重复连接只刷新店铺元数据和验证时间，不改变首次连接时间。
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
