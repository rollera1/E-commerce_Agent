package com.ecommerce.agent.integration.shopify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClientCredentialsShopifyAccessTokenProviderTest {

    private static final ShopifyStoreDomain DOMAIN =
            new ShopifyStoreDomain("sample-store.myshopify.com");
    private static final String CLIENT_SECRET = "never-log-this-secret";

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void exchangesCredentialsAndCachesTheToken() {
        server.expect(once(), requestTo(
                        "https://sample-store.myshopify.com/admin/oauth/access_token"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=client_credentials"),
                        org.hamcrest.Matchers.containsString("client_id=test-client"),
                        org.hamcrest.Matchers.containsString("client_secret=never-log-this-secret"))))
                .andRespond(withSuccess("""
                        {
                          "access_token": "short-lived-access-token",
                          "scope": "write_products,read_orders",
                          "expires_in": 86399
                        }
                        """, MediaType.APPLICATION_JSON));
        var provider = provider();

        String first = provider.getAccessToken(DOMAIN);
        String second = provider.getAccessToken(DOMAIN);

        assertThat(first).isEqualTo("short-lived-access-token");
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void rejectsInvalidCredentialsWithoutPuttingTheSecretInTheError() {
        server.expect(requestTo(
                        "https://sample-store.myshopify.com/admin/oauth/access_token"))
                .andRespond(withUnauthorizedRequest());
        var provider = provider();

        assertThatThrownBy(() -> provider.getAccessToken(DOMAIN))
                .isInstanceOf(ShopifyIntegrationException.class)
                .hasMessage("Shopify client credentials were rejected")
                .message()
                .doesNotContain(CLIENT_SECRET);
    }

    @Test
    void credentialsToStringIsAlwaysRedacted() {
        var credentials = new ShopifyClientCredentials("test-client", CLIENT_SECRET);

        assertThat(credentials.toString())
                .isEqualTo("ShopifyClientCredentials[redacted]")
                .doesNotContain(CLIENT_SECRET);
    }

    private ClientCredentialsShopifyAccessTokenProvider provider() {
        return new ClientCredentialsShopifyAccessTokenProvider(
                builder,
                new ShopifyClientCredentials("test-client", CLIENT_SECRET),
                Clock.fixed(Instant.parse("2026-08-29T07:00:00Z"), ZoneOffset.UTC));
    }
}
