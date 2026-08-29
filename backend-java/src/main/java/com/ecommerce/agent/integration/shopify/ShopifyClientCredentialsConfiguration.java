package com.ecommerce.agent.integration.shopify;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.storeconnection.ShopifyStoreConnectionManager;
import com.ecommerce.agent.storeconnection.ShopifyStoreConnectionRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShopifyProperties.class)
@ConditionalOnProperty(prefix = "shopify", name = "enabled", havingValue = "true")
public class ShopifyClientCredentialsConfiguration {

    @Bean
    ShopifyAccessTokenProvider shopifyAccessTokenProvider(ShopifyProperties properties) {
        return new ClientCredentialsShopifyAccessTokenProvider(
                RestClient.builder(),
                new ShopifyClientCredentials(
                        properties.getClientId(),
                        properties.getClientSecret()),
                Clock.systemUTC());
    }

    @Bean
    ShopifyAdminGateway shopifyAdminGateway(
            ShopifyAccessTokenProvider tokenProvider,
            ShopifyProperties properties) {
        return new ShopifyGraphqlAdminClient(
                RestClient.builder(),
                tokenProvider,
                properties.getApiVersion());
    }

    @Bean
    ShopifyStoreConnectionManager shopifyStoreConnectionManager(
            ShopifyAdminGateway shopifyAdminGateway,
            ShopifyStoreConnectionRepository repository) {
        return new ShopifyStoreConnectionManager(
                shopifyAdminGateway,
                repository,
                Clock.systemUTC());
    }
}
