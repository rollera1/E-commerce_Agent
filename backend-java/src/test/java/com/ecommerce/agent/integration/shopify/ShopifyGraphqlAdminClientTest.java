package com.ecommerce.agent.integration.shopify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ShopifyGraphqlAdminClientTest {

    private MockRestServiceServer server;
    private ShopifyGraphqlAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ShopifyGraphqlAdminClient(builder, ignored -> "secret-test-token", "2026-07");
    }

    @Test
    void readsShopInformationThroughGraphql() {
        server.expect(once(), requestTo(
                        "https://sample-store.myshopify.com/admin/api/2026-07/graphql.json"))
                .andExpect(method(POST))
                .andExpect(header("X-Shopify-Access-Token", "secret-test-token"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "shop": {
                              "id": "gid://shopify/Shop/123",
                              "name": "Sample Store",
                              "myshopifyDomain": "sample-store.myshopify.com",
                              "currencyCode": "USD",
                              "primaryDomain": {"host": "shop.example.com"}
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var shop = client.fetchShopInfo(new ShopifyStoreDomain("sample-store.myshopify.com"));

        assertThat(shop.name()).isEqualTo("Sample Store");
        assertThat(shop.myshopifyDomain()).isEqualTo("sample-store.myshopify.com");
        assertThat(shop.primaryDomain()).isEqualTo("shop.example.com");
        assertThat(shop.currencyCode()).isEqualTo("USD");
        server.verify();
    }

    @Test
    void convertsUnauthorizedResponseWithoutExposingToken() {
        server.expect(requestTo(
                        "https://sample-store.myshopify.com/admin/api/2026-07/graphql.json"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.fetchShopInfo(
                        new ShopifyStoreDomain("sample-store.myshopify.com")))
                .isInstanceOf(ShopifyIntegrationException.class)
                .extracting("code")
                .isEqualTo(ShopifyErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void convertsForbiddenResponseToInsufficientScope() {
        server.expect(requestTo(
                        "https://sample-store.myshopify.com/admin/api/2026-07/graphql.json"))
                .andRespond(withForbiddenRequest());

        assertThatThrownBy(() -> client.fetchShopInfo(
                        new ShopifyStoreDomain("sample-store.myshopify.com")))
                .isInstanceOf(ShopifyIntegrationException.class)
                .extracting("code")
                .isEqualTo(ShopifyErrorCode.INSUFFICIENT_SCOPE);
    }

    @Test
    void convertsGraphqlErrorsWithoutReturningRemoteDetails() {
        server.expect(requestTo(
                        "https://sample-store.myshopify.com/admin/api/2026-07/graphql.json"))
                .andRespond(withSuccess("""
                        {"errors":[{"message":"remote detail must stay internal"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchShopInfo(
                        new ShopifyStoreDomain("sample-store.myshopify.com")))
                .isInstanceOf(ShopifyIntegrationException.class)
                .hasMessage("Shopify rejected the GraphQL query")
                .message()
                .doesNotContain("remote detail");
    }
}
