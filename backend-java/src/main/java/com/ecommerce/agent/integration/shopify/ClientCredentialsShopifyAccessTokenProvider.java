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

/** 使用 Client Credentials Grant 获取并缓存 Shopify 短期访问令牌。 */
public class ClientCredentialsShopifyAccessTokenProvider
        implements ShopifyAccessTokenProvider {

    // 在实际过期前五分钟刷新，避免请求途中令牌失效。
    private static final Duration REFRESH_EARLY_BY = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final ShopifyClientCredentials credentials;
    private final Clock clock;
    // volatile 保证多个请求线程能看到最新缓存令牌。
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
        // 无锁读取仍然可用的缓存令牌，减少同步开销。
        CachedToken current = cachedToken;
        if (isUsable(current, storeDomain)) {
            return current.value();
        }
        return refreshToken(storeDomain);
    }

    private synchronized String refreshToken(ShopifyStoreDomain storeDomain) {
        // 进入锁后再次检查，避免多个线程依次重复申请令牌。
        CachedToken current = cachedToken;
        if (isUsable(current, storeDomain)) {
            return current.value();
        }

        // Shopify 的令牌端点要求 application/x-www-form-urlencoded 表单。
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
                        "Shopify 返回了无效的访问令牌响应");
            }

            cachedToken = new CachedToken(
                    storeDomain,
                    response.accessToken(),
                    clock.instant().plusSeconds(response.expiresIn()));
            return cachedToken.value();
        } catch (RestClientResponseException exception) {
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.INVALID_CREDENTIALS,
                    "Shopify 拒绝了客户端凭据");
        } catch (RestClientException exception) {
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.REMOTE_UNAVAILABLE,
                    "Shopify 令牌接口当前不可用",
                    exception);
        }
    }

    private boolean isUsable(CachedToken token, ShopifyStoreDomain storeDomain) {
        return token != null
                && token.storeDomain().equals(storeDomain)
                && clock.instant().isBefore(token.expiresAt().minus(REFRESH_EARLY_BY));
    }

    private record CachedToken(
            // 令牌所属店铺，避免跨店铺复用。
            ShopifyStoreDomain storeDomain,
            // 实际访问令牌。
            String value,
            // Shopify 返回的令牌过期时间。
            Instant expiresAt) {
    }

    /** Shopify 令牌接口的响应结构。 */
    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            String scope,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn) {
    }
}
