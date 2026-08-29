package com.ecommerce.agent.web;

import com.ecommerce.agent.storeconnection.ShopifyConnectionService;
import com.ecommerce.agent.storeconnection.ShopifyConnectionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopify/connection")
public class ShopifyConnectionController {

    private final ShopifyConnectionService connectionService;

    public ShopifyConnectionController(ShopifyConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public ShopifyConnectionStatus status() {
        return connectionService.status();
    }

    @PostMapping
    public ShopifyConnectionStatus connect() {
        return connectionService.connect();
    }
}
