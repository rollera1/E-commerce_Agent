package com.ecommerce.agent.integration.shopify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.storeconnection.ShopifyStoreConnectionManager;
import com.ecommerce.agent.storeconnection.ShopifyStoreConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ShopifyClientCredentialsConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ShopifyClientCredentialsConfiguration.class)
            .withBean(
                    ShopifyStoreConnectionRepository.class,
                    () -> mock(ShopifyStoreConnectionRepository.class));

    @Test
    void staysDisabledWithoutCredentials() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ShopifyAccessTokenProvider.class);
            assertThat(context).doesNotHaveBean(ShopifyAdminGateway.class);
            assertThat(context).doesNotHaveBean(ShopifyStoreConnectionManager.class);
        });
    }

    @Test
    void wiresTheCompleteConnectionFlowWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "shopify.enabled=true",
                        "shopify.shop-domain=sample-store.myshopify.com",
                        "shopify.api-version=2026-07",
                        "shopify.client-id=test-client",
                        "shopify.client-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(ShopifyAccessTokenProvider.class);
                    assertThat(context).hasSingleBean(ShopifyAdminGateway.class);
                    assertThat(context).hasSingleBean(ShopifyStoreConnectionManager.class);
                    assertThat(context.getBean(ShopifyProperties.class).toString())
                            .doesNotContain("test-secret");
                });
    }
}
