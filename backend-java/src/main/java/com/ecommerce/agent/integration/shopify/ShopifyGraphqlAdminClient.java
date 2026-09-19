package com.ecommerce.agent.integration.shopify;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyShopInfo;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Shopify Admin GraphQL API 的 HTTP 客户端。 */
public class ShopifyGraphqlAdminClient implements ShopifyAdminGateway {

    // 当前连接验收只读取店铺身份、域名和币种等非敏感元数据。
    private static final String SHOP_QUERY = """
            query EcommerceAgentShopInfo {
              shop {
                id
                name
                myshopifyDomain
                currencyCode
                primaryDomain {
                  host
                }
              }
            }
            """;

    private final RestClient restClient;
    private final ShopifyAccessTokenProvider tokenProvider;
    private final String apiVersion;

    public ShopifyGraphqlAdminClient(
            RestClient.Builder restClientBuilder,
            ShopifyAccessTokenProvider tokenProvider,
            String apiVersion) {
        this.restClient = restClientBuilder.build();
        this.tokenProvider = tokenProvider;
        this.apiVersion = apiVersion;
    }

    @Override
    public ShopifyShopInfo fetchShopInfo(ShopifyStoreDomain storeDomain) {
        // API 版本固定在路径中，避免 Shopify 默认版本变化影响项目。
        String endpoint = "https://%s/admin/api/%s/graphql.json"
                .formatted(storeDomain.value(), apiVersion);

        try {
            GraphqlEnvelope response = restClient.post()
                    .uri(endpoint)
                    .header("X-Shopify-Access-Token", tokenProvider.getAccessToken(storeDomain))
                    .body(Map.of("query", SHOP_QUERY))
                    .retrieve()
                    .body(GraphqlEnvelope.class);
            return mapResponse(response);
        } catch (RestClientResponseException exception) {
            throw translateHttpError(exception);
        } catch (RestClientException exception) {
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.REMOTE_UNAVAILABLE,
                    "Shopify Admin API 当前不可用",
                    exception);
        }
    }

    private ShopifyShopInfo mapResponse(GraphqlEnvelope response) {
        // HTTP 200 仍可能包含 GraphQL errors，因此还要检查响应正文。
        if (response == null || response.data() == null || response.data().shop() == null) {
            if (response != null && response.errors() != null && !response.errors().isEmpty()) {
                throw new ShopifyIntegrationException(
                        ShopifyErrorCode.GRAPHQL_ERROR,
                        "Shopify 拒绝了 GraphQL 查询");
            }
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.INVALID_RESPONSE,
                    "Shopify 返回的响应不完整");
        }

        Shop shop = response.data().shop();
        String primaryDomain = shop.primaryDomain() == null ? null : shop.primaryDomain().host();
        return new ShopifyShopInfo(
                shop.id(),
                shop.name(),
                shop.myshopifyDomain(),
                primaryDomain,
                shop.currencyCode());
    }

    private ShopifyIntegrationException translateHttpError(RestClientResponseException exception) {
        // 将外部 HTTP 状态转换为项目内部稳定的错误类型。
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 401) {
            return new ShopifyIntegrationException(
                    ShopifyErrorCode.INVALID_CREDENTIALS,
                    "Shopify 凭据无效或已过期");
        }
        if (status.value() == 403) {
            return new ShopifyIntegrationException(
                    ShopifyErrorCode.INSUFFICIENT_SCOPE,
                    "Shopify 应用缺少请求所需的访问权限");
        }
        return new ShopifyIntegrationException(
                ShopifyErrorCode.REMOTE_UNAVAILABLE,
                "Shopify Admin API 返回了 HTTP " + status.value());
    }

    /** Shopify GraphQL 响应最外层结构。 */
    private record GraphqlEnvelope(GraphqlData data, List<GraphqlError> errors) {
    }

    private record GraphqlData(Shop shop) {
    }

    private record Shop(
            String id,
            String name,
            String myshopifyDomain,
            String currencyCode,
            PrimaryDomain primaryDomain) {
    }

    private record PrimaryDomain(String host) {
    }

    private record GraphqlError(String message) {
    }
}
