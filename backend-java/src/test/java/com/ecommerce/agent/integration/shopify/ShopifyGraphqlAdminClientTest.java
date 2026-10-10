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
import com.ecommerce.agent.saleschannel.ShopifyProductCommand;
import com.ecommerce.agent.saleschannel.ShopifyVariantCommand;
import java.math.BigDecimal;
import java.util.List;
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
                .hasMessage("Shopify 拒绝了 GraphQL 查询")
                .message()
                .doesNotContain("remote detail");
    }

    @Test
    void readsLocationsAndPublicationsWithoutExposingCredentials() {
        expectSuccess("""
                {"data":{"locations":{"nodes":[{"id":"loc-1","name":"Main","isActive":true}]}}}
                """);
        expectSuccess("""
                {"data":{"publications":{"nodes":[{"id":"pub-1","autoPublish":false,"catalog":{"title":"Online Store"}}]}}}
                """);

        var locations = client.fetchLocations(domain());
        var publications = client.fetchPublications(domain());

        assertThat(locations).extracting("name").containsExactly("Main");
        assertThat(publications).extracting("title").containsExactly("Online Store");
        server.verify();
    }

    @Test
    void createsDraftVariantsThenActivatesAndPublishes() {
        expectSuccess("""
                {"data":{"productCreate":{"product":{"id":"gid://shopify/Product/1","status":"DRAFT","media":{"nodes":[]}},"userErrors":[]}}}
                """);
        expectSuccess("""
                {"data":{"productVariantsBulkCreate":{"productVariants":[{"id":"variant-1","inventoryItem":{"id":"inventory-1"}}],"userErrors":[]}}}
                """);
        expectSuccess("""
                {"data":{"productUpdate":{"product":{"id":"gid://shopify/Product/1","status":"ACTIVE"},"userErrors":[]}}}
                """);
        expectSuccess("""
                {"data":{"publishablePublish":{"publishable":{"publishedOnPublication":true},"userErrors":[]}}}
                """);

        var draft = client.createDraft(domain(), new ShopifyProductCommand(
                "Lamp", "<p>Lamp</p>", "Lamp", "Lamp meta", List.of("lamp"),
                List.of(), List.of("Black")));
        var variants = client.createVariants(domain(), draft.productId(), "loc-1", List.of(
                new ShopifyVariantCommand("source-v1", "Black", "SKU-1", new BigDecimal("19.99"), 10)));
        client.activateProduct(domain(), draft.productId());
        client.publishProduct(domain(), draft.productId(), "pub-1");

        assertThat(draft.mediaReady()).isTrue();
        assertThat(variants).extracting("variantId").containsExactly("variant-1");
        server.verify();
    }

    @Test
    void mapsMutationUserErrorsToStableBusinessError() {
        expectSuccess("""
                {"data":{"productCreate":{"product":null,"userErrors":[{"field":["product","title"],"message":"bad title"}]}}}
                """);

        assertThatThrownBy(() -> client.createDraft(domain(), new ShopifyProductCommand(
                "Lamp", "<p>Lamp</p>", "Lamp", "Lamp meta", List.of("lamp"), List.of(), List.of("Default"))))
                .isInstanceOf(ShopifyIntegrationException.class)
                .extracting("code")
                .isEqualTo(ShopifyErrorCode.USER_ERROR);
    }

    @Test
    void replacesDraftMediaByDeletingExistingMediaThenAddingNewImages() {
        expectSuccess("""
                {"data":{"product":{"media":{"nodes":[{"id":"gid://shopify/MediaImage/1","preview":{"status":"READY"}}]}}}}
                """);
        expectSuccess("""
                {"data":{"productDeleteMedia":{"deletedMediaIds":["gid://shopify/MediaImage/1"],"mediaUserErrors":[]}}}
                """);
        expectSuccess("""
                {"data":{"productUpdate":{"product":{"id":"gid://shopify/Product/1","media":{"nodes":[{"id":"gid://shopify/MediaImage/2","preview":{"status":"PROCESSING"}}]}},"userErrors":[]}}}
                """);

        client.replaceProductMedia(
                domain(), "gid://shopify/Product/1", "Lamp",
                List.of("https://example.invalid/new-image.jpg"));

        server.verify();
    }

    private void expectSuccess(String body) {
        server.expect(requestTo("https://sample-store.myshopify.com/admin/api/2026-07/graphql.json"))
                .andExpect(method(POST))
                .andExpect(header("X-Shopify-Access-Token", "secret-test-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private ShopifyStoreDomain domain() {
        return new ShopifyStoreDomain("sample-store.myshopify.com");
    }
}
