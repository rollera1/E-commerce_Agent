package com.ecommerce.agent.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.agent.integration.shopify.ShopifyErrorCode;
import com.ecommerce.agent.integration.shopify.ShopifyIntegrationException;
import com.ecommerce.agent.storeconnection.ShopifyAuthorizationStatus;
import com.ecommerce.agent.storeconnection.ShopifyConnectionService;
import com.ecommerce.agent.storeconnection.ShopifyConnectionState;
import com.ecommerce.agent.storeconnection.ShopifyConnectionStatus;
import com.ecommerce.agent.storeconnection.ShopifyNotConfiguredException;
import com.ecommerce.agent.storeconnection.ShopifyStoreConnectionView;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShopifyConnectionController.class)
class ShopifyConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopifyConnectionService connectionService;

    @Test
    void reportsDisconnectedWithoutExposingCredentials() throws Exception {
        when(connectionService.status()).thenReturn(new ShopifyConnectionStatus(
                true,
                true,
                "sample-store.myshopify.com",
                ShopifyConnectionState.DISCONNECTED,
                null));

        mockMvc.perform(get("/api/shopify/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.configuredShopDomain").value("sample-store.myshopify.com"))
                .andExpect(jsonPath("$.state").value("DISCONNECTED"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void connectsAndReturnsVerifiedShopMetadata() throws Exception {
        ShopifyStoreConnectionView store = new ShopifyStoreConnectionView(
                "gid://shopify/Shop/123",
                "sample-store.myshopify.com",
                "Sample Store",
                "shop.example.com",
                "USD",
                ShopifyAuthorizationStatus.CONNECTED,
                Instant.parse("2026-08-29T05:00:00Z"),
                Instant.parse("2026-08-29T05:00:00Z"));
        when(connectionService.connect()).thenReturn(new ShopifyConnectionStatus(
                true,
                true,
                "sample-store.myshopify.com",
                ShopifyConnectionState.CONNECTED,
                store));

        mockMvc.perform(post("/api/shopify/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONNECTED"))
                .andExpect(jsonPath("$.store.shopName").value("Sample Store"))
                .andExpect(jsonPath("$.store.currencyCode").value("USD"));
    }

    @Test
    void returnsActionableErrorWhenShopifyIsNotConfigured() throws Exception {
        when(connectionService.connect()).thenThrow(
                new ShopifyNotConfiguredException("Shopify connection is not enabled."));

        mockMvc.perform(post("/api/shopify/connection"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.service").value("shopify"))
                .andExpect(jsonPath("$.errorCode").value("SHOPIFY_NOT_CONFIGURED"));
    }

    @Test
    void returnsSanitizedShopifyError() throws Exception {
        when(connectionService.connect()).thenThrow(new ShopifyIntegrationException(
                ShopifyErrorCode.INSUFFICIENT_SCOPE,
                "Shopify rejected the configured access scopes"));

        mockMvc.perform(post("/api/shopify/connection"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.service").value("shopify"))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_SCOPE"))
                .andExpect(jsonPath("$.message").value(
                        "Shopify rejected the configured access scopes"));
    }
}
