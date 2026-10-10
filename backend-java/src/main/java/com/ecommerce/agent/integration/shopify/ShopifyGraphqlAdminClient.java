package com.ecommerce.agent.integration.shopify;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyCreatedVariant;
import com.ecommerce.agent.saleschannel.ShopifyDraftProduct;
import com.ecommerce.agent.saleschannel.ShopifyLocation;
import com.ecommerce.agent.saleschannel.ShopifyProductCommand;
import com.ecommerce.agent.saleschannel.ShopifyPublication;
import com.ecommerce.agent.saleschannel.ShopifyShopInfo;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import com.ecommerce.agent.saleschannel.ShopifyVariantCommand;
import com.ecommerce.agent.saleschannel.ShopifyVariantUpdate;
import tools.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Shopify Admin GraphQL 的唯一远端 Adapter。 */
public class ShopifyGraphqlAdminClient implements ShopifyAdminGateway {
    private static final String SHOP_QUERY = """
            query EcommerceAgentShopInfo { shop { id name myshopifyDomain currencyCode primaryDomain { host } } }
            """;
    private static final String LOCATIONS_QUERY = """
            query EcommerceAgentLocations { locations(first: 50) { nodes { id name isActive } } }
            """;
    private static final String PUBLICATIONS_QUERY = """
            query EcommerceAgentPublications { publications(first: 50) { nodes { id autoPublish catalog { title } } } }
            """;
    private static final String CREATE_PRODUCT = """
            mutation EcommerceAgentCreateDraft($product: ProductCreateInput!, $media: [CreateMediaInput!]) {
              productCreate(product: $product, media: $media) {
                product { id status media(first: 100) { nodes { preview { status } } } }
                userErrors { field message }
              }
            }
            """;
    private static final String CREATE_VARIANTS = """
            mutation EcommerceAgentCreateVariants($productId: ID!, $variants: [ProductVariantsBulkInput!]!) {
              productVariantsBulkCreate(productId: $productId, variants: $variants, strategy: REMOVE_STANDALONE_VARIANT) {
                productVariants { id inventoryItem { id } }
                userErrors { field message }
              }
            }
            """;
    private static final String UPDATE_PRODUCT = """
            mutation EcommerceAgentUpdateDraft($product: ProductUpdateInput!) {
              productUpdate(product: $product) { product { id status } userErrors { field message } }
            }
            """;
    private static final String UPDATE_VARIANTS = """
            mutation EcommerceAgentUpdateVariants($productId: ID!, $variants: [ProductVariantsBulkInput!]!) {
              productVariantsBulkUpdate(productId: $productId, variants: $variants) {
                productVariants { id }
                userErrors { field message }
              }
            }
            """;
    private static final String MEDIA_QUERY = """
            query EcommerceAgentProductMedia($id: ID!) {
              product(id: $id) { media(first: 100) { nodes { id preview { status } } } }
            }
            """;
    private static final String DELETE_MEDIA = """
            mutation EcommerceAgentDeleteProductMedia($productId: ID!, $mediaIds: [ID!]!) {
              productDeleteMedia(productId: $productId, mediaIds: $mediaIds) {
                deletedMediaIds
                mediaUserErrors { field message }
              }
            }
            """;
    private static final String ADD_MEDIA = """
            mutation EcommerceAgentAddProductMedia($product: ProductUpdateInput!, $media: [CreateMediaInput!]) {
              productUpdate(product: $product, media: $media) {
                product { id media(first: 100) { nodes { id preview { status } } } }
                userErrors { field message }
              }
            }
            """;

    private final RestClient restClient;
    private final ShopifyAccessTokenProvider tokenProvider;
    private final String apiVersion;

    public ShopifyGraphqlAdminClient(
            RestClient.Builder builder,
            ShopifyAccessTokenProvider tokenProvider,
            String apiVersion) {
        this.restClient = builder.build();
        this.tokenProvider = tokenProvider;
        this.apiVersion = apiVersion;
    }

    @Override
    public ShopifyShopInfo fetchShopInfo(ShopifyStoreDomain domain) {
        JsonNode shop = required(execute(domain, SHOP_QUERY, Map.of()), "shop");
        return new ShopifyShopInfo(
                text(shop, "id"), text(shop, "name"), text(shop, "myshopifyDomain"),
                shop.path("primaryDomain").path("host").asText(null), text(shop, "currencyCode"));
    }

    @Override
    public List<ShopifyLocation> fetchLocations(ShopifyStoreDomain domain) {
        return stream(required(execute(domain, LOCATIONS_QUERY, Map.of()), "locations").path("nodes"))
                .map(node -> new ShopifyLocation(text(node, "id"), text(node, "name"), node.path("isActive").asBoolean()))
                .filter(ShopifyLocation::active)
                .toList();
    }

    @Override
    public List<ShopifyPublication> fetchPublications(ShopifyStoreDomain domain) {
        return stream(required(execute(domain, PUBLICATIONS_QUERY, Map.of()), "publications").path("nodes"))
                .map(node -> new ShopifyPublication(
                        text(node, "id"), node.path("catalog").path("title").asText("未命名渠道"),
                        node.path("autoPublish").asBoolean()))
                .toList();
    }

    @Override
    public ShopifyDraftProduct createDraft(ShopifyStoreDomain domain, ShopifyProductCommand command) {
        Map<String, Object> product = productInput(command);
        product.put("status", "DRAFT");
        List<Map<String, Object>> media = mediaInput(command.imageUrls(), command.title());
        JsonNode payload = required(execute(domain, CREATE_PRODUCT, Map.of("product", product, "media", media)), "productCreate");
        rejectUserErrors(payload);
        JsonNode created = required(payload, "product");
        return new ShopifyDraftProduct(text(created, "id"), mediaReadyNodes(created.path("media").path("nodes")));
    }

    @Override
    public List<ShopifyCreatedVariant> createVariants(
            ShopifyStoreDomain domain, String productId, String locationId,
            List<ShopifyVariantCommand> variants) {
        List<Map<String, Object>> inputs = variants.stream().map(variant -> Map.<String, Object>of(
                "optionValues", List.of(Map.of("optionName", "Title", "name", variant.optionValue())),
                "price", variant.price(),
                "inventoryPolicy", "DENY",
                "inventoryItem", Map.of("sku", variant.sku(), "tracked", true),
                "inventoryQuantities", List.of(Map.of(
                        "locationId", locationId, "availableQuantity", variant.inventoryQuantity()))))
                .toList();
        JsonNode payload = required(execute(domain, CREATE_VARIANTS, Map.of(
                "productId", productId, "variants", inputs)), "productVariantsBulkCreate");
        rejectUserErrors(payload);
        return stream(payload.path("productVariants"))
                .map(node -> new ShopifyCreatedVariant(text(node, "id"), text(required(node, "inventoryItem"), "id")))
                .toList();
    }

    @Override
    public void updateDraft(
            ShopifyStoreDomain domain, String productId, ShopifyProductCommand command,
            List<ShopifyVariantUpdate> variants) {
        Map<String, Object> product = productInput(command);
        product.remove("productOptions");
        product.put("id", productId);
        JsonNode productPayload = required(execute(domain, UPDATE_PRODUCT, Map.of("product", product)), "productUpdate");
        rejectUserErrors(productPayload);
        if (!variants.isEmpty()) {
            List<Map<String, Object>> inputs = variants.stream()
                    .map(value -> Map.<String, Object>of("id", value.variantId(), "price", value.price()))
                    .toList();
            JsonNode variantPayload = required(execute(domain, UPDATE_VARIANTS, Map.of(
                    "productId", productId, "variants", inputs)), "productVariantsBulkUpdate");
            rejectUserErrors(variantPayload);
        }
    }

    @Override
    public void replaceProductMedia(
            ShopifyStoreDomain domain, String productId, String altText, List<String> imageUrls) {
        JsonNode product = required(execute(domain, MEDIA_QUERY, Map.of("id", productId)), "product");
        List<String> mediaIds = stream(product.path("media").path("nodes"))
                .map(node -> text(node, "id"))
                .toList();
        if (!mediaIds.isEmpty()) {
            JsonNode deleted = required(execute(domain, DELETE_MEDIA, Map.of(
                    "productId", productId, "mediaIds", mediaIds)), "productDeleteMedia");
            rejectErrors(deleted, "mediaUserErrors");
        }
        JsonNode added = required(execute(domain, ADD_MEDIA, Map.of(
                "product", Map.of("id", productId),
                "media", mediaInput(imageUrls, altText))), "productUpdate");
        rejectUserErrors(added);
    }

    @Override
    public boolean mediaReady(ShopifyStoreDomain domain, String productId) {
        JsonNode product = required(execute(domain, MEDIA_QUERY, Map.of("id", productId)), "product");
        return mediaReadyNodes(product.path("media").path("nodes"));
    }

    @Override
    public void activateProduct(ShopifyStoreDomain domain, String productId) {
        JsonNode payload = required(execute(domain, UPDATE_PRODUCT, Map.of(
                "product", Map.of("id", productId, "status", "ACTIVE"))), "productUpdate");
        rejectUserErrors(payload);
    }

    @Override
    public void publishProduct(ShopifyStoreDomain domain, String productId, String publicationId) {
        String mutation = """
                mutation EcommerceAgentPublish($id: ID!, $input: [PublicationInput!]!, $publicationId: ID!) {
                  publishablePublish(id: $id, input: $input) {
                    publishable { publishedOnPublication(publicationId: $publicationId) }
                    userErrors { field message }
                  }
                }
                """;
        JsonNode payload = required(execute(domain, mutation, Map.of(
                "id", productId, "publicationId", publicationId,
                "input", List.of(Map.of("publicationId", publicationId)))), "publishablePublish");
        rejectUserErrors(payload);
        if (!payload.path("publishable").path("publishedOnPublication").asBoolean()) {
            throw new ShopifyIntegrationException(ShopifyErrorCode.INVALID_RESPONSE, "Shopify 未确认商品已发布");
        }
    }

    private Map<String, Object> productInput(ShopifyProductCommand command) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("title", command.title());
        product.put("descriptionHtml", command.descriptionHtml());
        product.put("seo", Map.of("title", command.seoTitle(), "description", command.seoDescription()));
        product.put("tags", command.tags());
        product.put("productOptions", List.of(Map.of(
                "name", "Title",
                "values", command.optionValues().stream().map(value -> Map.of("name", value)).toList())));
        return product;
    }

    private List<Map<String, Object>> mediaInput(List<String> imageUrls, String altText) {
        return imageUrls.stream()
                .map(url -> Map.<String, Object>of(
                        "mediaContentType", "IMAGE", "originalSource", url, "alt", altText))
                .toList();
    }

    private JsonNode execute(ShopifyStoreDomain domain, String query, Map<String, Object> variables) {
        String endpoint = "https://%s/admin/api/%s/graphql.json".formatted(domain.value(), apiVersion);
        try {
            JsonNode response = restClient.post().uri(endpoint)
                    .header("X-Shopify-Access-Token", tokenProvider.getAccessToken(domain))
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve().body(JsonNode.class);
            if (response == null) throw invalidResponse();
            if (response.path("errors").isArray() && !response.path("errors").isEmpty()) {
                throw new ShopifyIntegrationException(ShopifyErrorCode.GRAPHQL_ERROR, "Shopify 拒绝了 GraphQL 查询");
            }
            JsonNode data = response.path("data");
            if (data.isMissingNode() || data.isNull()) throw invalidResponse();
            return data;
        } catch (RestClientResponseException exception) {
            throw translateHttpError(exception);
        } catch (ShopifyIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ShopifyIntegrationException(ShopifyErrorCode.REMOTE_UNAVAILABLE, "Shopify Admin API 当前不可用", exception);
        }
    }

    private void rejectUserErrors(JsonNode payload) {
        rejectErrors(payload, "userErrors");
    }

    private void rejectErrors(JsonNode payload, String field) {
        JsonNode errors = payload.path(field);
        if (errors.isArray() && !errors.isEmpty()) {
            String message = stream(errors).map(node -> node.path("message").asText("未知错误"))
                    .collect(Collectors.joining("；"));
            throw new ShopifyIntegrationException(ShopifyErrorCode.USER_ERROR, "Shopify 拒绝了业务操作：" + message);
        }
    }

    private boolean mediaReadyNodes(JsonNode nodes) {
        if (!nodes.isArray() || nodes.isEmpty()) return true;
        return stream(nodes).allMatch(node -> "READY".equals(node.path("preview").path("status").asText()));
    }

    private ShopifyIntegrationException translateHttpError(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 401) return new ShopifyIntegrationException(ShopifyErrorCode.INVALID_CREDENTIALS, "Shopify 凭据无效或已过期");
        if (status.value() == 403) return new ShopifyIntegrationException(ShopifyErrorCode.INSUFFICIENT_SCOPE, "Shopify 应用缺少请求所需的访问权限");
        if (status.value() == 429) return new ShopifyIntegrationException(ShopifyErrorCode.RATE_LIMITED, "Shopify 请求受到限流");
        return new ShopifyIntegrationException(ShopifyErrorCode.REMOTE_UNAVAILABLE, "Shopify Admin API 返回了 HTTP " + status.value());
    }

    private JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) throw invalidResponse();
        return value;
    }
    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) throw invalidResponse();
        return value;
    }
    private ShopifyIntegrationException invalidResponse() {
        return new ShopifyIntegrationException(ShopifyErrorCode.INVALID_RESPONSE, "Shopify 返回的响应不完整");
    }
    private java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        if (!array.isArray()) throw invalidResponse();
        return StreamSupport.stream(array.spliterator(), false);
    }
}
