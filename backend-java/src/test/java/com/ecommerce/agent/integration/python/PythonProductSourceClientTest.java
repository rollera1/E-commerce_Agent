package com.ecommerce.agent.integration.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonProductSourceClientTest {

    @Test
    void constructsFromTheConfiguredPythonServiceUrl() {
        var properties = new PythonServiceProperties(URI.create("http://localhost:8005"));

        var client = new PythonProductSourceClient(properties);

        assertThat(client).isNotNull();
    }

    @Test
    void mapsThePythonStandardProductContract() {
        var builder = RestClient.builder().baseUrl("http://localhost:8005");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PythonProductSourceClient(builder.build());
        server.expect(requestTo(
                        "http://localhost:8005/internal/product-sources/products"
                                + "?keyword=desk&page=1&pageSize=20"))
                .andRespond(withSuccess(responseBody(), MediaType.APPLICATION_JSON));

        var result = client.list("desk", 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().sourceProductId()).isEqualTo("fake-desk-lamp-001");
        assertThat(result.items().getFirst().inventoryTotal()).isEqualTo(123);
        assertThat(result.items().getFirst().price().amount())
                .isEqualByComparingTo(new BigDecimal("29.90"));
        assertThat(result.items().getFirst().variants().getFirst().sku())
                .isEqualTo("FAKE-LAMP-BLK");
        server.verify();
    }

    private String responseBody() {
        return """
                {
                  "items": [{
                    "source": "FAKE",
                    "sourceProductId": "fake-desk-lamp-001",
                    "title": "Adjustable LED Desk Lamp",
                    "description": "A compact dimmable desk lamp for home offices.",
                    "images": ["https://example.invalid/products/desk-lamp-001.jpg"],
                    "price": {"amount": "29.90", "currency": "USD"},
                    "inventoryTotal": 123,
                    "variants": [{
                      "sourceVariantId": "fake-desk-lamp-black",
                      "sku": "FAKE-LAMP-BLK",
                      "title": "Black",
                      "price": {"amount": "29.90", "currency": "USD"},
                      "inventoryAvailable": 50
                    }],
                    "sourceUrl": "https://example.invalid/products/fake-desk-lamp-001",
                    "fetchedAt": "2026-09-02T00:00:00Z"
                  }],
                  "page": 1,
                  "pageSize": 20,
                  "total": 1
                }
                """;
    }
}
