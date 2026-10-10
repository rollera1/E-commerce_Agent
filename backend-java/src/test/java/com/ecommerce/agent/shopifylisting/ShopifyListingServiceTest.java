package com.ecommerce.agent.shopifylisting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.agent.copyseo.AgentExecution;
import com.ecommerce.agent.copyseo.AgentOutcome;
import com.ecommerce.agent.copyseo.AgentTrace;
import com.ecommerce.agent.copyseo.CopySeoContent;
import com.ecommerce.agent.copyseo.CopySeoGenerationService;
import com.ecommerce.agent.copyseo.CopySeoGenerationView;
import com.ecommerce.agent.copyseo.GenerationStatus;
import com.ecommerce.agent.copyseo.ReviewStatus;
import com.ecommerce.agent.integration.shopify.ShopifyProperties;
import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyCreatedVariant;
import com.ecommerce.agent.saleschannel.ShopifyDraftProduct;
import com.ecommerce.agent.saleschannel.ShopifyLocation;
import com.ecommerce.agent.saleschannel.ShopifyPublication;
import com.ecommerce.agent.sourceproduct.Money;
import com.ecommerce.agent.sourceproduct.SourceProduct;
import com.ecommerce.agent.sourceproduct.SourceProductQueryService;
import com.ecommerce.agent.sourceproduct.SourceVariant;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ShopifyListingServiceTest {
    private final ShopifyListingTaskRepository taskRepository = mock(ShopifyListingTaskRepository.class);
    private final ShopifyListingVariantRepository variantRepository = mock(ShopifyListingVariantRepository.class);
    private final CopySeoGenerationService generationService = mock(CopySeoGenerationService.class);
    private final SourceProductQueryService productService = mock(SourceProductQueryService.class);
    private final ShopifyAdminGateway gateway = mock(ShopifyAdminGateway.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ShopifyAdminGateway> gatewayProvider = mock(ObjectProvider.class);
    private final Map<String, ShopifyListingTask> tasks = new HashMap<>();
    private final Map<String, List<ShopifyListingVariant>> variants = new HashMap<>();
    private ShopifyListingService service;

    @BeforeEach
    void setUp() {
        ShopifyProperties properties = new ShopifyProperties();
        properties.setShopDomain("sample-store.myshopify.com");
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.fetchLocations(any())).thenReturn(List.of(new ShopifyLocation("loc-1", "Main", true)));
        when(gateway.fetchPublications(any())).thenReturn(List.of(new ShopifyPublication("pub-1", "Online Store", false)));
        when(productService.get("source-1")).thenReturn(product());
        when(generationService.get("generation-1")).thenReturn(generation(ReviewStatus.ACCEPTED));
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            ShopifyListingTask value = invocation.getArgument(0);
            tasks.put(value.generationId(), value);
            return value;
        });
        when(taskRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(tasks.get(invocation.getArgument(0))));
        when(taskRepository.existsById(anyString())).thenAnswer(invocation -> tasks.containsKey(invocation.getArgument(0)));
        when(variantRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ShopifyListingVariant> values = (List<ShopifyListingVariant>) invocation.getArgument(0);
            variants.put("generation-1", new ArrayList<>(values));
            return values;
        });
        when(variantRepository.findByGenerationIdOrderByIdAsc("generation-1"))
                .thenAnswer(ignored -> variants.getOrDefault("generation-1", List.of()));
        service = new ShopifyListingService(
                taskRepository, variantRepository, generationService, productService,
                gatewayProvider, properties,
                Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void manualPublishStopsAfterCompleteDraft() {
        when(gateway.createDraft(any(), any())).thenReturn(new ShopifyDraftProduct("gid://shopify/Product/1", true));
        when(gateway.createVariants(any(), anyString(), anyString(), any())).thenReturn(List.of(
                new ShopifyCreatedVariant("variant-1", "inventory-1"),
                new ShopifyCreatedVariant("variant-2", "inventory-2")));
        service.configure("generation-1", "source-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false));

        ShopifyListingView result = service.processAccepted("generation-1");

        assertThat(result.status()).isEqualTo(ListingStatus.READY_TO_PUBLISH);
        assertThat(result.shopifyProductId()).isEqualTo("gid://shopify/Product/1");
        verify(gateway, never()).publishProduct(any(), anyString(), anyString());
    }

    @Test
    void createsDraftSeparatelyAfterContentAcceptanceAndIsIdempotent() {
        when(gateway.createDraft(any(), any())).thenReturn(new ShopifyDraftProduct("gid://shopify/Product/1", true));
        when(gateway.createVariants(any(), anyString(), anyString(), any())).thenReturn(List.of(
                new ShopifyCreatedVariant("variant-1", "inventory-1"),
                new ShopifyCreatedVariant("variant-2", "inventory-2")));
        assertThat(service.createAcceptedDraft("generation-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false)).status())
                .isEqualTo(ListingStatus.READY_TO_PUBLISH);
        service.createAcceptedDraft("generation-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false));
        verify(gateway).createDraft(any(), any());
        verify(gateway, never()).publishProduct(any(), anyString(), anyString());
    }

    @Test
    void pendingContentCannotConfigureOrCreateDraft() {
        when(generationService.get("generation-1")).thenReturn(generation(ReviewStatus.PENDING_REVIEW));
        assertThatThrownBy(() -> service.createAcceptedDraft("generation-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false)))
                .isInstanceOf(ListingConflictException.class).hasMessageContaining("接受文案");
        verify(productService, never()).get(anyString());
        verify(gateway, never()).createDraft(any(), any());
    }

    @Test
    void unknownAndZeroInventoryCannotCreateDraft() {
        for (Integer inventory : new Integer[] {null, 0}) {
            SourceProduct original = product();
            when(productService.get("source-1")).thenReturn(new SourceProduct(
                    original.source(), original.sourceProductId(), original.title(), original.description(),
                    original.images(), original.price(), List.of(
                    new SourceVariant("v1", "SKU-1", "Black", original.price(), inventory),
                    new SourceVariant("v2", "SKU-2", "White", original.price(), 8)),
                    original.sourceUrl(), original.fetchedAt()));
            assertThatThrownBy(() -> service.createAcceptedDraft("generation-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false)))
                    .isInstanceOf(ListingConflictException.class).hasMessageContaining("库存");
        }
        verify(gateway, never()).createDraft(any(), any());
        assertThat(tasks).isEmpty();
    }

    @Test
    void authorizedAutoPublishCompletesTheWorkflow() {
        when(gateway.createDraft(any(), any())).thenReturn(new ShopifyDraftProduct("gid://shopify/Product/1", true));
        when(gateway.createVariants(any(), anyString(), anyString(), any())).thenReturn(List.of(
                new ShopifyCreatedVariant("variant-1", "inventory-1"),
                new ShopifyCreatedVariant("variant-2", "inventory-2")));
        service.configure("generation-1", "source-1", settings(ReviewMode.AUTO, PublishMode.AUTO, true));

        ShopifyListingView result = service.processAccepted("generation-1");

        assertThat(result.status()).isEqualTo(ListingStatus.PUBLISHED);
        verify(gateway).activateProduct(any(), anyString());
        verify(gateway).publishProduct(any(), anyString(), anyString());
    }

    @Test
    void automaticPublishRequiresExplicitAuthorization() {
        assertThatThrownBy(() -> service.configure(
                "generation-1", "source-1", settings(ReviewMode.MANUAL, PublishMode.AUTO, false)))
                .isInstanceOf(ListingConflictException.class)
                .hasMessageContaining("授权");
    }

    @Test
    void processingTheSameReadyTaskDoesNotCreateAnotherProduct() {
        when(gateway.createDraft(any(), any())).thenReturn(new ShopifyDraftProduct("gid://shopify/Product/1", true));
        when(gateway.createVariants(any(), anyString(), anyString(), any())).thenReturn(List.of(
                new ShopifyCreatedVariant("variant-1", "inventory-1"),
                new ShopifyCreatedVariant("variant-2", "inventory-2")));
        service.configure("generation-1", "source-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false));
        service.processAccepted("generation-1");

        service.processAccepted("generation-1");

        verify(gateway).createDraft(any(), any());
    }

    @Test
    void reopeningDraftCanReplaceImagesWithoutCreatingAnotherProduct() {
        when(gateway.createDraft(any(), any())).thenReturn(new ShopifyDraftProduct("gid://shopify/Product/1", true));
        when(gateway.createVariants(any(), anyString(), anyString(), any())).thenReturn(List.of(
                new ShopifyCreatedVariant("variant-1", "inventory-1"),
                new ShopifyCreatedVariant("variant-2", "inventory-2")));
        when(gateway.mediaReady(any(), anyString())).thenReturn(true);
        service.configure("generation-1", "source-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false));
        service.processAccepted("generation-1");
        service.reopen("generation-1");
        ShopifyListingSettings changed = new ShopifyListingSettings(
                ReviewMode.MANUAL, PublishMode.MANUAL, "loc-1", "pub-1", false,
                List.of("https://example.invalid/replacement.jpg"),
                List.of(new VariantPrice("v1", new BigDecimal("19.99")),
                        new VariantPrice("v2", new BigDecimal("21.99"))));

        service.updateSettings("generation-1", changed);
        ShopifyListingView result = service.processAccepted("generation-1");

        assertThat(result.status()).isEqualTo(ListingStatus.READY_TO_PUBLISH);
        verify(gateway).replaceProductMedia(
                any(), anyString(), anyString(), any());
        verify(gateway).createDraft(any(), any());
    }

    @Test
    void inventoryLocationCannotChangeAfterDraftCreation() {
        when(gateway.createDraft(any(), any())).thenReturn(new ShopifyDraftProduct("gid://shopify/Product/1", true));
        when(gateway.createVariants(any(), anyString(), anyString(), any())).thenReturn(List.of(
                new ShopifyCreatedVariant("variant-1", "inventory-1"),
                new ShopifyCreatedVariant("variant-2", "inventory-2")));
        service.configure("generation-1", "source-1", settings(ReviewMode.MANUAL, PublishMode.MANUAL, false));
        service.processAccepted("generation-1");
        service.reopen("generation-1");
        when(gateway.fetchLocations(any())).thenReturn(List.of(
                new ShopifyLocation("loc-1", "Main", true),
                new ShopifyLocation("loc-2", "Secondary", true)));
        ShopifyListingSettings moved = new ShopifyListingSettings(
                ReviewMode.MANUAL, PublishMode.MANUAL, "loc-2", "pub-1", false,
                List.of("https://example.invalid/image.jpg"),
                List.of(new VariantPrice("v1", new BigDecimal("19.99")),
                        new VariantPrice("v2", new BigDecimal("21.99"))));

        assertThatThrownBy(() -> service.updateSettings("generation-1", moved))
                .isInstanceOf(ListingConflictException.class)
                .hasMessageContaining("库存地点");
    }

    private ShopifyListingSettings settings(ReviewMode review, PublishMode publish, boolean authorized) {
        return new ShopifyListingSettings(
                review, publish, "loc-1", "pub-1", authorized,
                List.of("https://example.invalid/image.jpg"),
                List.of(new VariantPrice("v1", new BigDecimal("19.99")),
                        new VariantPrice("v2", new BigDecimal("21.99"))));
    }

    private SourceProduct product() {
        return new SourceProduct(
                "FAKE", "source-1", "Lamp", "Source description",
                List.of("https://example.invalid/image.jpg"),
                new Money(new BigDecimal("8.00"), "USD"),
                List.of(
                        new SourceVariant("v1", "SKU-1", "Black", new Money(new BigDecimal("8.00"), "USD"), 10),
                        new SourceVariant("v2", "SKU-2", "White", new Money(new BigDecimal("9.00"), "USD"), 8)),
                "https://example.invalid/product", Instant.EPOCH);
    }

    private CopySeoGenerationView generation(ReviewStatus reviewStatus) {
        return new CopySeoGenerationView(
                "generation-1", "source-1", "US", "en", GenerationStatus.SUCCEEDED, reviewStatus,
                new CopySeoContent("Generated Lamp", List.of("Bright", "Compact"),
                        "Generated description", "Generated Lamp", "Generated meta", List.of("lamp")),
                new AgentExecution("COPY_SEO", "v1", "p1", "fake", 1),
                new AgentTrace("DONE", AgentOutcome.COMPLETED, List.of(), List.of(), 1, 0, List.of(), null),
                null);
    }
}
