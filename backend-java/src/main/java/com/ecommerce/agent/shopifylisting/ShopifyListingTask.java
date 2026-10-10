package com.ecommerce.agent.shopifylisting;

import com.ecommerce.agent.copyseo.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "shopify_listing_task")
public class ShopifyListingTask {
    @Id @Column(name = "generation_id", nullable = false, length = 36)
    private String generationId;
    @Column(name = "source_product_id", nullable = false, length = 200)
    private String sourceProductId;
    @Enumerated(EnumType.STRING) @Column(name = "review_mode", nullable = false)
    private ReviewMode reviewMode;
    @Enumerated(EnumType.STRING) @Column(name = "publish_mode", nullable = false)
    private PublishMode publishMode;
    @Column(name = "location_id", nullable = false, length = 100)
    private String locationId;
    @Column(name = "publication_id", nullable = false, length = 100)
    private String publicationId;
    @Column(name = "auto_publish_authorized", nullable = false)
    private boolean autoPublishAuthorized;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ListingStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "current_step", nullable = false, length = 32)
    private ListingStep currentStep;
    @Column(name = "shopify_product_id", length = 100)
    private String shopifyProductId;
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "image_urls", nullable = false, columnDefinition = "TEXT")
    private List<String> imageUrls;
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "applied_image_urls", nullable = false, columnDefinition = "TEXT")
    private List<String> appliedImageUrls;
    @Column(name = "media_ready", nullable = false)
    private boolean mediaReady;
    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;
    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopifyListingTask() {}

    public static ShopifyListingTask configured(
            String generationId, String sourceProductId, ShopifyListingSettings settings, Instant now) {
        var task = new ShopifyListingTask();
        task.generationId = generationId;
        task.sourceProductId = sourceProductId;
        task.reviewMode = settings.reviewMode();
        task.publishMode = settings.publishMode();
        task.locationId = settings.locationId();
        task.publicationId = settings.publicationId();
        task.autoPublishAuthorized = settings.autoPublishAuthorized();
        task.status = ListingStatus.CONFIGURED;
        task.currentStep = ListingStep.CONFIGURATION;
        task.imageUrls = List.copyOf(settings.imageUrls());
        task.appliedImageUrls = List.of();
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public void startDraft(Instant now) { transition(ListingStatus.CREATING_DRAFT, ListingStep.DRAFT, now); }
    public void draftCreated(String productId, boolean ready, Instant now) {
        this.shopifyProductId = productId;
        this.appliedImageUrls = this.imageUrls;
        this.mediaReady = ready;
        transition(ListingStatus.DRAFT_CREATED, ListingStep.VARIANTS, now);
    }
    public void writingVariants(Instant now) { transition(ListingStatus.WRITING_VARIANTS, ListingStep.VARIANTS, now); }
    public void mediaPending(Instant now) { transition(ListingStatus.MEDIA_PENDING, ListingStep.MEDIA, now); }
    public void ready(Instant now) { this.mediaReady = true; transition(ListingStatus.READY_TO_PUBLISH, ListingStep.ACTIVATE, now); }
    public void activating(Instant now) { transition(ListingStatus.ACTIVATING, ListingStep.ACTIVATE, now); }
    public void publishing(Instant now) { transition(ListingStatus.PUBLISHING, ListingStep.PUBLISH, now); }
    public void published(Instant now) { transition(ListingStatus.PUBLISHED, ListingStep.COMPLETE, now); }
    public void fail(ListingStep step, String code, String message, Instant now) {
        this.status = ListingStatus.FAILED;
        this.currentStep = step;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.updatedAt = now;
    }
    public void reopen(Instant now) {
        if (status == ListingStatus.PUBLISHED) throw new ListingConflictException("已发布商品不能退回编辑");
        this.status = ListingStatus.EDITING;
        this.currentStep = ListingStep.CONFIGURATION;
        this.autoPublishAuthorized = false;
        this.updatedAt = now;
    }
    public void reconfigure(ShopifyListingSettings settings, Instant now) {
        if (status != ListingStatus.CONFIGURED && status != ListingStatus.EDITING) {
            throw new ListingConflictException("当前上架任务不能修改配置");
        }
        if (shopifyProductId != null && !locationId.equals(settings.locationId())) {
            throw new ListingConflictException("草稿创建后不能更换库存地点");
        }
        this.reviewMode = settings.reviewMode();
        this.publishMode = settings.publishMode();
        this.locationId = settings.locationId();
        this.publicationId = settings.publicationId();
        this.autoPublishAuthorized = settings.autoPublishAuthorized();
        this.imageUrls = List.copyOf(settings.imageUrls());
        this.updatedAt = now;
    }
    public boolean imagesChanged() { return !imageUrls.equals(appliedImageUrls); }
    public void imagesApplied(Instant now) {
        this.appliedImageUrls = this.imageUrls;
        this.mediaReady = false;
        this.updatedAt = now;
    }
    private void transition(ListingStatus status, ListingStep step, Instant now) {
        this.status = status;
        this.currentStep = step;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public String generationId() { return generationId; }
    public String sourceProductId() { return sourceProductId; }
    public ReviewMode reviewMode() { return reviewMode; }
    public PublishMode publishMode() { return publishMode; }
    public String locationId() { return locationId; }
    public String publicationId() { return publicationId; }
    public boolean autoPublishAuthorized() { return autoPublishAuthorized; }
    public ListingStatus status() { return status; }
    public ListingStep currentStep() { return currentStep; }
    public String shopifyProductId() { return shopifyProductId; }
    public List<String> imageUrls() { return imageUrls; }
    public boolean mediaReady() { return mediaReady; }
    public String lastErrorCode() { return lastErrorCode; }
    public String lastErrorMessage() { return lastErrorMessage; }
}
