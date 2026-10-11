package com.ecommerce.agent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.agent.copyseo.AgentExecution;
import com.ecommerce.agent.copyseo.AgentOutcome;
import com.ecommerce.agent.copyseo.AgentTrace;
import com.ecommerce.agent.copyseo.CopySeoContent;
import com.ecommerce.agent.copyseo.CopySeoGenerationRequest;
import com.ecommerce.agent.copyseo.CopySeoGenerationService;
import com.ecommerce.agent.copyseo.CopySeoGenerationView;
import com.ecommerce.agent.copyseo.CopySeoGenerationNotFoundException;
import com.ecommerce.agent.copyseo.CopySeoReviewConflictException;
import com.ecommerce.agent.copyseo.GenerationStatus;
import com.ecommerce.agent.copyseo.ReviewStatus;
import com.ecommerce.agent.copyseo.ToolCallStatus;
import com.ecommerce.agent.copyseo.ToolCallTrace;
import com.ecommerce.agent.productselection.ProductSelectionConflictException;
import com.ecommerce.agent.productselection.ProductSelectionService;
import com.ecommerce.agent.shopifylisting.ShopifyListingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CopySeoGenerationController.class)
class CopySeoGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CopySeoGenerationService generationService;

    @MockitoBean
    private ProductSelectionService selectionService;

    @MockitoBean
    private ShopifyListingService listingService;

    @Test
    void generatesCopyThatRequiresMerchantReview() throws Exception {
        when(selectionService.requireConfirmedSourceProduct("selection-1"))
                .thenReturn("fake-desk-lamp-001");
        when(generationService.generate(any(CopySeoGenerationRequest.class)))
                .thenReturn(generation());

        mockMvc.perform(post("/api/product-selections/selection-1/copy-seo-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetMarket":"US","language":"en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.content.title").value(
                        "Adjustable LED Desk Lamp for Focused Workspaces"))
                .andExpect(jsonPath("$.trace.outcome").value("COMPLETED"))
                .andExpect(jsonPath("$.trace.toolCalls[0].tool")
                        .value("get_product_detail"))
                .andExpect(jsonPath("$.execution.apiKey").doesNotExist());
        verifyNoInteractions(listingService);
    }

    @Test
    void returnsAcceptedForAnAsynchronousGeneration() throws Exception {
        when(selectionService.requireConfirmedSourceProduct("selection-1"))
                .thenReturn("fake-desk-lamp-001");
        when(generationService.generate(any(CopySeoGenerationRequest.class)))
                .thenReturn(submittedGeneration());

        mockMvc.perform(post("/api/product-selections/selection-1/copy-seo-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetMarket":"US","language":"en"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void getsPersistedGenerationStatus() throws Exception {
        when(generationService.get("generation-1")).thenReturn(submittedGeneration());

        mockMvc.perform(get("/api/copy-seo-generations/generation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value("generation-1"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void cancelsAnAsynchronousGeneration() throws Exception {
        var cancelled = new CopySeoGenerationView(
                "generation-1", "fake-desk-lamp-001", "US", "en",
                GenerationStatus.CANCELLED, ReviewStatus.NOT_APPLICABLE,
                null, new AgentExecution("COPY_SEO", "pending", "pending", "pending", 0),
                null, null);
        when(generationService.cancel("generation-1")).thenReturn(cancelled);

        mockMvc.perform(post("/api/copy-seo-generations/generation-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void merchantCanEditPendingGeneratedContent() throws Exception {
        when(generationService.updateContent(
                eq("9d1625ec-9c39-45c8-afc0-24e2e45b2158"), any(CopySeoContent.class)))
                .thenReturn(generation());

        mockMvc.perform(put("/api/copy-seo-generations/9d1625ec-9c39-45c8-afc0-24e2e45b2158/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Adjustable LED Desk Lamp for Focused Workspaces",
                                  "bulletPoints":["Adjustable lighting"],
                                  "description":"A generated description.",
                                  "seoTitle":"Adjustable LED Desk Lamp | Home Office Lighting",
                                  "metaDescription":"A compact adjustable LED desk lamp for home offices.",
                                  "tags":["desk lamp"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.content.title").value(
                        "Adjustable LED Desk Lamp for Focused Workspaces"));
    }

    @Test
    void merchantCanAcceptPendingGeneratedContent() throws Exception {
        when(generationService.accept("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(generation(ReviewStatus.ACCEPTED));

        mockMvc.perform(post(
                        "/api/copy-seo-generations/9d1625ec-9c39-45c8-afc0-24e2e45b2158/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ACCEPTED"));
        verifyNoInteractions(listingService);
    }

    @Test
    void merchantCanRejectPendingGeneratedContent() throws Exception {
        when(generationService.reject("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(generation(ReviewStatus.REJECTED));

        mockMvc.perform(post(
                        "/api/copy-seo-generations/9d1625ec-9c39-45c8-afc0-24e2e45b2158/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
    }

    @Test
    void returnsNotFoundWhenGenerationDoesNotExist() throws Exception {
        when(generationService.accept("missing-generation"))
                .thenThrow(new CopySeoGenerationNotFoundException("missing-generation"));

        mockMvc.perform(post("/api/copy-seo-generations/missing-generation/accept"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COPY_SEO_GENERATION_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenReviewDecisionCannotBeChanged() throws Exception {
        when(generationService.reject("accepted-generation"))
                .thenThrow(new CopySeoReviewConflictException(
                        "已接受的文案与 SEO 内容不能拒绝"));

        mockMvc.perform(post("/api/copy-seo-generations/accepted-generation/reject"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COPY_SEO_REVIEW_CONFLICT"));
    }

    @Test
    void rejectsGenerationRequestWhenMarketOrLanguageIsMissing() throws Exception {
        mockMvc.perform(post("/api/product-selections/selection-1/copy-seo-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCopyGenerationBeforeMerchantSelectionConfirmation() throws Exception {
        when(selectionService.requireConfirmedSourceProduct("selection-1"))
                .thenThrow(new ProductSelectionConflictException("必须先由商家确认候选商品"));

        mockMvc.perform(post("/api/product-selections/selection-1/copy-seo-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetMarket":"US","language":"en"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_SELECTION_CONFLICT"));
    }

    @Test
    void rejectsEditedContentWhenListsAreMissing() throws Exception {
        mockMvc.perform(put("/api/copy-seo-generations/generation-id/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Desk Lamp",
                                  "description":"A description.",
                                  "seoTitle":"Desk Lamp",
                                  "metaDescription":"A meta description."
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private CopySeoGenerationView generation() {
        return generation(ReviewStatus.PENDING_REVIEW);
    }

    private CopySeoGenerationView generation(ReviewStatus reviewStatus) {
        return new CopySeoGenerationView(
                "9d1625ec-9c39-45c8-afc0-24e2e45b2158",
                "fake-desk-lamp-001",
                "US",
                "en",
                GenerationStatus.SUCCEEDED,
                reviewStatus,
                new CopySeoContent(
                        "Adjustable LED Desk Lamp for Focused Workspaces",
                        List.of("Adjustable lighting"),
                        "A generated description.",
                        "Adjustable LED Desk Lamp | Home Office Lighting",
                        "A compact adjustable LED desk lamp for home offices.",
                        List.of("desk lamp")),
                new AgentExecution("COPY_SEO", "fake-v1", "copy-seo-v1", "fake", 0),
                new AgentTrace(
                        "AWAIT_MERCHANT_REVIEW",
                        AgentOutcome.COMPLETED,
                        List.of("COLLECT_PRODUCT_CONTEXT", "AWAIT_MERCHANT_REVIEW"),
                        List.of(new ToolCallTrace(
                                "get_product_detail",
                                ToolCallStatus.SUCCEEDED,
                                0,
                                Map.of("variantCount", 1),
                                null)),
                        1,
                        0,
                        List.of(),
                        "MERCHANT_REVIEW"),
                null);
    }

    private CopySeoGenerationView submittedGeneration() {
        return new CopySeoGenerationView(
                "generation-1",
                "fake-desk-lamp-001",
                "US",
                "en",
                GenerationStatus.SUBMITTED,
                ReviewStatus.NOT_APPLICABLE,
                null,
                new AgentExecution("COPY_SEO", "pending", "pending", "pending", 0),
                null,
                null);
    }
}
