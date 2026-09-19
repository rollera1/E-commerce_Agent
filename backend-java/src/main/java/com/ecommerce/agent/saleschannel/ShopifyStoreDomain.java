package com.ecommerce.agent.saleschannel;

import java.util.Locale;
import java.util.regex.Pattern;

/** 经过格式校验和标准化的 Shopify 永久店铺域名。 */
public record ShopifyStoreDomain(
        // 不带协议和路径的 *.myshopify.com 域名。
        String value) {

    // 只允许 *.myshopify.com，避免将令牌发送到任意主机。
    private static final Pattern VALID_DOMAIN = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.myshopify\\.com$");

    public ShopifyStoreDomain {
        if (value == null) {
            throw new IllegalArgumentException("Shopify 店铺域名不能为空");
        }
        // 去除首尾空格并统一为小写，便于比较和持久化。
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID_DOMAIN.matcher(value).matches()) {
            throw new IllegalArgumentException("Shopify 店铺域名必须是有效的 *.myshopify.com 主机名");
        }
    }
}
