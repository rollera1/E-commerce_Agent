package com.ecommerce.agent.integration.shopify;

import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ClientCredentialsShopifyAccessTokenProvider
        implements ShopifyAccessTokenProvider {

    private static final Duration REFRESH_EARLY_BY = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final ShopifyClientCredentials credentials;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public ClientCredentialsShopifyAccessTokenProvider(
            RestClient.Builder restClientBuilder,
            ShopifyClientCredentials credentials,
            Clock clock) {
        this.restClient = restClientBuilder.build();
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    public String getAccessToken(ShopifyStoreDomain storeDomain) {
        CachedToken current = cachedToken;
        if (isUsable(current, storeDomain)) {
            return current.value();
        }
        return refreshToken(storeDomain);
    }

    private synchronized String refreshToken(ShopifyStoreDomain storeDomain) {
        CachedToken current = cachedToken;
        if (isUsable(current, storeDomain)) {
            return current.value();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", credentials.clientId());
        form.add("client_secret", credentials.clientSecret());

        try {
            TokenResponse response = restClient.post()
                    .uri("https://%s/admin/oauth/access_token".formatted(storeDomain.value()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.expiresIn() <= 0) {
                throw new ShopifyIntegrationException(
                        ShopifyErrorCode.INVALID_RESPONSE,
                        "Shopify returned an invalid access token response");
            }

            cachedToken = new CachedToken(
                    storeDomain,
                    response.accessToken(),
                    clock.instant().plusSeconds(response.expiresIn()));
            return cachedToken.value();
        } catch (RestClientResponseException exception) {
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.INVALID_CREDENTIALS,
                    "Shopify client credentials were rejected");
        } catch (RestClientException exception) {
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.REMOTE_UNAVAILABLE,
                    "Shopify token endpoint is unavailable",
                    exception);
        }
    }

    private boolean isUsable(CachedToken token, ShopifyStoreDomain storeDomain) {
        return token != null
                && token.storeDomain().equals(storeDomain)
                && clock.instant().isBefore(token.expiresAt().minus(REFRESH_EARLY_BY));
    }

    private record CachedToken(
            ShopifyStoreDomain storeDomain,
            String value,
            Instant expiresAt) {
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            String scope,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn) {
    }
}
