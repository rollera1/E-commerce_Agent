package com.ecommerce.agent.copyseo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.agent.shopifylisting.ReviewMode;
import com.ecommerce.agent.shopifylisting.ShopifyListingService;
import com.ecommerce.agent.shopifylisting.ShopifyListingView;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopySeoAgentRunPollerTest {

    @Test
    void continuesConfiguredAutoListingAfterAgentSuccess() {
        var generationService = mock(CopySeoGenerationService.class);
        var listingService = mock(ShopifyListingService.class);
        var listing = mock(ShopifyListingView.class);
        var generation = new CopySeoGenerationView(
                "generation-1",
                "source-1",
                "US",
                "en",
                GenerationStatus.SUCCEEDED,
                ReviewStatus.PENDING_REVIEW,
                null,
                new AgentExecution("COPY_SEO", "v1", "p1", "model", 1),
                null,
                null);
        when(generationService.pendingGenerationIds()).thenReturn(List.of("generation-1"));
        when(generationService.poll("generation-1")).thenReturn(generation);
        when(listingService.isConfigured("generation-1")).thenReturn(true);
        when(listingService.get("generation-1")).thenReturn(listing);
        when(listing.reviewMode()).thenReturn(ReviewMode.AUTO);

        new CopySeoAgentRunPoller(generationService, listingService).poll();

        verify(generationService).accept("generation-1");
        verify(listingService).processAccepted("generation-1");
    }
}
