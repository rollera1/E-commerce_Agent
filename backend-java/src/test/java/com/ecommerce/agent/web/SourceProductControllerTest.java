package com.ecommerce.agent.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.agent.sourceproduct.Money;
import com.ecommerce.agent.sourceproduct.SourceProduct;
import com.ecommerce.agent.sourceproduct.SourceProductPage;
import com.ecommerce.agent.sourceproduct.SourceProductQueryService;
import com.ecommerce.agent.sourceproduct.SourceVariant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SourceProductController.class)
class SourceProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceProductQueryService queryService;

    @Test
    void listsStandardizedProductsFromPython() throws Exception {
        var page = new SourceProductPage(List.of(product()), 1, 20, 1);
        when(queryService.list("desk", 1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/source-products")
                        .queryParam("keyword", "desk")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].source").value("FAKE"))
                .andExpect(jsonPath("$.items[0].sourceProductId").value("fake-desk-lamp-001"))
                .andExpect(jsonPath("$.items[0].price.amount").value(29.90));
    }

    @Test
    void getsStandardizedProductDetail() throws Exception {
        when(queryService.get("fake-desk-lamp-001")).thenReturn(product());

        mockMvc.perform(get("/api/source-products/fake-desk-lamp-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Adjustable LED Desk Lamp"))
                .andExpect(jsonPath("$.variants[0].sku").value("FAKE-LAMP-BLK"));
    }

    private SourceProduct product() {
        var price = new Money(new BigDecimal("29.90"), "USD");
        return new SourceProduct(
                "FAKE",
                "fake-desk-lamp-001",
                "Adjustable LED Desk Lamp",
                "A compact dimmable desk lamp for home offices.",
                List.of("https://example.invalid/products/desk-lamp-001.jpg"),
                price,
                List.of(new SourceVariant(
                        "fake-desk-lamp-black", "FAKE-LAMP-BLK", "Black", price, 50)),
                "https://example.invalid/products/fake-desk-lamp-001",
                Instant.parse("2026-09-02T00:00:00Z"));
    }
}
