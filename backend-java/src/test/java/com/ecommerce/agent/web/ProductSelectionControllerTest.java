package com.ecommerce.agent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.agent.productselection.ProductSelectionConfirmationView;
import com.ecommerce.agent.productselection.ProductSelectionSearchRequest;
import com.ecommerce.agent.productselection.ProductSelectionSearchView;
import com.ecommerce.agent.productselection.ProductSelectionService;
import com.ecommerce.agent.productselection.ProductSelectionStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductSelectionController.class)
class ProductSelectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductSelectionService selectionService;

    @Test
    void merchantSearchesCandidatesThroughTheSelectionAgent() throws Exception {
        when(selectionService.search(any(ProductSelectionSearchRequest.class)))
                .thenReturn(new ProductSelectionSearchView(
                        "selection-1",
                        "宠物牵引绳",
                        "pet leash",
                        "CANDIDATES_FOUND",
                        ProductSelectionStatus.AWAITING_CONFIRMATION,
                        List.of(),
                        Map.of("searchCount", 1)));

        mockMvc.perform(post("/api/product-selections/searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"宠物牵引绳","limit":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectionId").value("selection-1"))
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"));
    }

    @Test
    void merchantExplicitlyConfirmsOneCandidate() throws Exception {
        when(selectionService.confirm("selection-1", "cj-1"))
                .thenReturn(new ProductSelectionConfirmationView(
                        "selection-1", ProductSelectionStatus.CONFIRMED, "cj-1"));

        mockMvc.perform(post("/api/product-selections/selection-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceProductId":"cj-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.selectedSourceProductId").value("cj-1"));
    }

    @Test
    void rejectsBlankSearchIntent() throws Exception {
        mockMvc.perform(post("/api/product-selections/searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"","limit":10}
                                """))
                .andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 10, 20, 50, 100})
    void acceptsRequestedCountWithinCjLimit(int limit) throws Exception {
        when(selectionService.search(any(ProductSelectionSearchRequest.class)))
                .thenReturn(new ProductSelectionSearchView("s", "lamp", "lamp", "NO_CANDIDATES",
                        ProductSelectionStatus.AWAITING_CONFIRMATION, List.of(), Map.of()));
        mockMvc.perform(post("/api/product-selections/searches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"lamp\",\"limit\":" + limit + "}"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(selectionService).search(new ProductSelectionSearchRequest("lamp", limit));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 101})
    void rejectsInvalidRequestedCount(int limit) throws Exception {
        mockMvc.perform(post("/api/product-selections/searches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"lamp\",\"limit\":" + limit + "}"))
                .andExpect(status().isBadRequest());
    }
}
