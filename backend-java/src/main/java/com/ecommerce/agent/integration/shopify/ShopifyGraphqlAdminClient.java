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

public class ShopifyGraphqlAdminClient implements ShopifyAdminGateway {

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
                    "Shopify Admin API is unavailable",
                    exception);
        }
    }

    private ShopifyShopInfo mapResponse(GraphqlEnvelope response) {
        if (response == null || response.data() == null || response.data().shop() == null) {
            if (response != null && response.errors() != null && !response.errors().isEmpty()) {
                throw new ShopifyIntegrationException(
                        ShopifyErrorCode.GRAPHQL_ERROR,
                        "Shopify rejected the GraphQL query");
            }
            throw new ShopifyIntegrationException(
                    ShopifyErrorCode.INVALID_RESPONSE,
                    "Shopify returned an incomplete response");
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
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 401) {
            return new ShopifyIntegrationException(
                    ShopifyErrorCode.INVALID_CREDENTIALS,
                    "Shopify credentials are invalid or expired");
        }
        if (status.value() == 403) {
            return new ShopifyIntegrationException(
                    ShopifyErrorCode.INSUFFICIENT_SCOPE,
                    "Shopify app does not have the required access scope");
        }
        return new ShopifyIntegrationException(
                ShopifyErrorCode.REMOTE_UNAVAILABLE,
                "Shopify Admin API returned HTTP " + status.value());
    }

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
