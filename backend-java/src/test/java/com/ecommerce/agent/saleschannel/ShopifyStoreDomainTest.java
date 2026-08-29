package com.ecommerce.agent.saleschannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ShopifyStoreDomainTest {

    @Test
    void normalizesAValidMyshopifyDomain() {
        var domain = new ShopifyStoreDomain("  Sample-Store.MyShopify.Com ");

        assertThat(domain.value()).isEqualTo("sample-store.myshopify.com");
    }

    @Test
    void rejectsUrlsAndNonShopifyHosts() {
        assertThatThrownBy(() -> new ShopifyStoreDomain(
                        "https://sample-store.myshopify.com/admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShopifyStoreDomain("attacker.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
