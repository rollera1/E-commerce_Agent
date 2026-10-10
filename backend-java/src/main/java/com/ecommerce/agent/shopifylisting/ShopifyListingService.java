package com.ecommerce.agent.shopifylisting;

import com.ecommerce.agent.copyseo.CopySeoGenerationService;
import com.ecommerce.agent.copyseo.CopySeoGenerationView;
import com.ecommerce.agent.copyseo.ReviewStatus;
import com.ecommerce.agent.integration.shopify.ShopifyErrorCode;
import com.ecommerce.agent.integration.shopify.ShopifyIntegrationException;
import com.ecommerce.agent.integration.shopify.ShopifyProperties;
import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyLocation;
import com.ecommerce.agent.saleschannel.ShopifyProductCommand;
import com.ecommerce.agent.saleschannel.ShopifyPublication;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import com.ecommerce.agent.saleschannel.ShopifyVariantCommand;
import com.ecommerce.agent.saleschannel.ShopifyVariantUpdate;
import com.ecommerce.agent.sourceproduct.SourceProduct;
import com.ecommerce.agent.sourceproduct.SourceProductQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

/** 持久化并推进 Shopify 草稿与发布工作流。 */
@Service
public class ShopifyListingService {
    private final ShopifyListingTaskRepository taskRepository;
    private final ShopifyListingVariantRepository variantRepository;
    private final CopySeoGenerationService generationService;
    private final SourceProductQueryService productQueryService;
    private final ObjectProvider<ShopifyAdminGateway> gatewayProvider;
    private final ShopifyProperties properties;
    private final Clock clock;

    @Autowired
    public ShopifyListingService(
            ShopifyListingTaskRepository taskRepository,
            ShopifyListingVariantRepository variantRepository,
            CopySeoGenerationService generationService,
            SourceProductQueryService productQueryService,
            ObjectProvider<ShopifyAdminGateway> gatewayProvider,
            ShopifyProperties properties) {
        this(taskRepository, variantRepository, generationService, productQueryService,
                gatewayProvider, properties, Clock.systemUTC());
    }

    ShopifyListingService(
            ShopifyListingTaskRepository taskRepository,
            ShopifyListingVariantRepository variantRepository,
            CopySeoGenerationService generationService,
            SourceProductQueryService productQueryService,
            ObjectProvider<ShopifyAdminGateway> gatewayProvider,
            ShopifyProperties properties,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.variantRepository = variantRepository;
        this.generationService = generationService;
        this.productQueryService = productQueryService;
        this.gatewayProvider = gatewayProvider;
        this.properties = properties;
        this.clock = clock;
    }

    public List<ShopifyLocation> locations() {
        return gateway().fetchLocations(domain());
    }

    public List<ShopifyPublication> publications() {
        return gateway().fetchPublications(domain());
    }

    public ShopifyListingView configure(
            String generationId, String sourceProductId, ShopifyListingSettings settings) {
        ShopifyListingTask existing = taskRepository.findById(generationId).orElse(null);
        if (existing != null) return view(existing);
        if (settings.publishMode() == PublishMode.AUTO && !settings.autoPublishAuthorized()) {
            throw new ListingConflictException("自动发布必须先完成任务级授权确认");
        }
        validateRemoteChoices(settings);
        SourceProduct product = productQueryService.get(sourceProductId);
        validateImages(settings.imageUrls());
        Map<String, BigDecimal> prices = new HashMap<>();
        settings.variantPrices().forEach(value -> prices.put(value.sourceVariantId(), value.price()));
        if (prices.size() != product.variants().size()) {
            throw new ListingConflictException("必须为每个来源变体填写唯一售价");
        }

        ShopifyListingTask task = ShopifyListingTask.configured(
                generationId, sourceProductId, settings, clock.instant());
        List<ShopifyListingVariant> variants = product.variants().stream().map(source -> {
            BigDecimal price = prices.get(source.sourceVariantId());
            if (price == null || price.signum() <= 0) throw new ListingConflictException("存在缺失或非法的变体售价");
            if (source.inventoryAvailable() == null || source.inventoryAvailable() <= 0) {
                throw new ListingConflictException("来源库存未知或不足，不能进入自动上架流程");
            }
            return ShopifyListingVariant.configured(
                    generationId, source.sourceVariantId(), normalizeOption(source.title()),
                    source.sku(), price, source.inventoryAvailable());
        }).toList();
        taskRepository.save(task);
        variantRepository.saveAll(variants);
        return view(task);
    }

    public ShopifyListingView updateSettings(String generationId, ShopifyListingSettings settings) {
        ShopifyListingTask task = find(generationId);
        if (settings.publishMode() == PublishMode.AUTO && !settings.autoPublishAuthorized()) {
            throw new ListingConflictException("自动发布必须先完成任务级授权确认");
        }
        validateRemoteChoices(settings);
        validateImages(settings.imageUrls());
        Map<String, BigDecimal> prices = new HashMap<>();
        settings.variantPrices().forEach(value -> prices.put(value.sourceVariantId(), value.price()));
        List<ShopifyListingVariant> variants = variants(generationId);
        if (prices.size() != variants.size()) throw new ListingConflictException("必须为每个来源变体填写唯一售价");
        variants.forEach(value -> {
            BigDecimal price = prices.get(value.sourceVariantId());
            if (price == null || price.signum() <= 0) throw new ListingConflictException("存在缺失或非法的变体售价");
            value.updatePrice(price);
        });
        task.reconfigure(settings, clock.instant());
        variantRepository.saveAll(variants);
        return view(taskRepository.save(task));
    }

    public boolean isConfigured(String generationId) {
        return taskRepository.existsById(generationId);
    }

    /** 文案审核与上架分离：只有已接受内容才能单独配置和创建草稿。 */
    public synchronized ShopifyListingView createAcceptedDraft(String generationId, ShopifyListingSettings settings) {
        CopySeoGenerationView generation = generationService.get(generationId);
        if (generation.reviewStatus() != ReviewStatus.ACCEPTED) {
            throw new ListingConflictException("请先接受文案，再创建 Shopify 草稿");
        }
        ShopifyListingTask existing = taskRepository.findById(generationId).orElse(null);
        if (existing == null) {
            configure(generationId, generation.sourceProductId(), settings);
        } else if (existing.status() == ListingStatus.EDITING) {
            updateSettings(generationId, settings);
        } else if (existing.status() != ListingStatus.CONFIGURED) {
            return view(existing);
        }
        return processAccepted(generationId);
    }

    public ShopifyListingView processAccepted(String generationId) {
        ShopifyListingTask task = find(generationId);
        CopySeoGenerationView generation = generationService.get(generationId);
        if (generation.reviewStatus() != ReviewStatus.ACCEPTED) {
            throw new ListingConflictException("文案尚未通过审核，不能创建 Shopify 草稿");
        }
        try {
            processDraft(task, generation);
            if (task.status() == ListingStatus.READY_TO_PUBLISH
                    && task.publishMode() == PublishMode.AUTO
                    && task.autoPublishAuthorized()) {
                publishInternal(task);
            }
        } catch (ShopifyIntegrationException exception) {
            task.fail(task.currentStep(), exception.getCode().name(), exception.getMessage(), clock.instant());
            taskRepository.save(task);
        }
        return view(task);
    }

    public ShopifyListingView publish(String generationId) {
        ShopifyListingTask task = find(generationId);
        if (task.status() != ListingStatus.READY_TO_PUBLISH
                && !(task.status() == ListingStatus.FAILED
                && (task.currentStep() == ListingStep.ACTIVATE || task.currentStep() == ListingStep.PUBLISH))) {
            throw new ListingConflictException("草稿尚未完整写入，不能发布");
        }
        try {
            publishInternal(task);
        } catch (ShopifyIntegrationException exception) {
            task.fail(task.currentStep(), exception.getCode().name(), exception.getMessage(), clock.instant());
            taskRepository.save(task);
        }
        return view(task);
    }

    public ShopifyListingView retry(String generationId) {
        ShopifyListingTask task = find(generationId);
        if (task.status() != ListingStatus.FAILED && task.status() != ListingStatus.MEDIA_PENDING) {
            return view(task);
        }
        if (task.status() == ListingStatus.FAILED
                && task.shopifyProductId() == null
                && ShopifyErrorCode.REMOTE_UNAVAILABLE.name().equals(task.lastErrorCode())) {
            throw new ListingConflictException("无法确认 Shopify 是否已创建商品，请人工核对后处理");
        }
        return processAccepted(generationId);
    }

    public ShopifyListingView reopen(String generationId) {
        ShopifyListingTask task = find(generationId);
        task.reopen(clock.instant());
        generationService.reopen(generationId);
        return view(taskRepository.save(task));
    }

    public ShopifyListingView get(String generationId) { return view(find(generationId)); }

    private void processDraft(ShopifyListingTask task, CopySeoGenerationView generation) {
        ShopifyAdminGateway gateway = gateway();
        ShopifyStoreDomain domain = domain();
        List<ShopifyListingVariant> variants = variants(task.generationId());
        ShopifyProductCommand command = productCommand(generation, task, variants);

        if (task.shopifyProductId() == null) {
            task.startDraft(clock.instant());
            taskRepository.save(task);
            var created = gateway.createDraft(domain, command);
            task.draftCreated(created.productId(), created.mediaReady(), clock.instant());
            taskRepository.save(task);
        } else if (task.status() == ListingStatus.EDITING) {
            List<ShopifyVariantUpdate> updates = variants.stream()
                    .filter(value -> value.shopifyVariantId() != null)
                    .map(value -> new ShopifyVariantUpdate(value.shopifyVariantId(), value.sellingPrice()))
                    .toList();
            gateway.updateDraft(domain, task.shopifyProductId(), command, updates);
            if (task.imagesChanged()) {
                gateway.replaceProductMedia(
                        domain, task.shopifyProductId(), generation.content().title(), task.imageUrls());
                task.imagesApplied(clock.instant());
                taskRepository.save(task);
            }
        }

        if (variants.stream().anyMatch(value -> value.shopifyVariantId() == null)) {
            task.writingVariants(clock.instant());
            taskRepository.save(task);
            List<ShopifyVariantCommand> commands = variants.stream().map(value -> new ShopifyVariantCommand(
                    value.sourceVariantId(), value.optionValue(), value.sku(),
                    value.sellingPrice(), value.sourceInventory())).toList();
            var created = gateway.createVariants(domain, task.shopifyProductId(), task.locationId(), commands);
            if (created.size() != variants.size()) {
                throw new ShopifyIntegrationException(ShopifyErrorCode.INVALID_RESPONSE, "Shopify 返回的变体数量不一致");
            }
            for (int index = 0; index < variants.size(); index++) {
                variants.get(index).bindShopify(created.get(index).variantId(), created.get(index).inventoryItemId());
            }
            variantRepository.saveAll(variants);
        }

        if (!task.mediaReady() && !gateway.mediaReady(domain, task.shopifyProductId())) {
            task.mediaPending(clock.instant());
            taskRepository.save(task);
            return;
        }
        task.ready(clock.instant());
        taskRepository.save(task);
    }

    private void publishInternal(ShopifyListingTask task) {
        ShopifyAdminGateway gateway = gateway();
        ShopifyStoreDomain domain = domain();
        task.activating(clock.instant());
        taskRepository.save(task);
        gateway.activateProduct(domain, task.shopifyProductId());
        task.publishing(clock.instant());
        taskRepository.save(task);
        gateway.publishProduct(domain, task.shopifyProductId(), task.publicationId());
        task.published(clock.instant());
        taskRepository.save(task);
    }

    private ShopifyProductCommand productCommand(
            CopySeoGenerationView generation, ShopifyListingTask task,
            List<ShopifyListingVariant> variants) {
        var content = generation.content();
        String bullets = content.bulletPoints().stream()
                .map(value -> "<li>" + escapeHtml(value) + "</li>")
                .collect(Collectors.joining());
        String html = "<ul>" + bullets + "</ul><p>"
                + escapeHtml(content.description()).replace("\n", "<br>") + "</p>";
        return new ShopifyProductCommand(
                content.title(), html, content.seoTitle(), content.metaDescription(),
                content.tags(), task.imageUrls(), variants.stream().map(ShopifyListingVariant::optionValue).toList());
    }

    private void validateRemoteChoices(ShopifyListingSettings settings) {
        boolean locationExists = locations().stream().anyMatch(value -> value.id().equals(settings.locationId()));
        boolean publicationExists = publications().stream().anyMatch(value -> value.id().equals(settings.publicationId()));
        if (!locationExists) throw new ListingConflictException("所选 Shopify Location 不可用");
        if (!publicationExists) throw new ListingConflictException("所选 Shopify Publication 不可用");
    }

    private ShopifyListingView view(ShopifyListingTask task) {
        List<ListingVariantView> values = variants(task.generationId()).stream()
                .map(value -> new ListingVariantView(
                        value.sourceVariantId(), value.sku(), value.optionValue(), value.sellingPrice(),
                        value.sourceInventory(), value.shopifyVariantId()))
                .toList();
        return new ShopifyListingView(
                task.generationId(), task.reviewMode(), task.publishMode(), task.locationId(),
                task.publicationId(), task.autoPublishAuthorized(), task.status(), task.currentStep(),
                task.shopifyProductId(), task.imageUrls(), task.mediaReady(),
                task.lastErrorCode(), task.lastErrorMessage(), values);
    }

    private List<ShopifyListingVariant> variants(String generationId) {
        return variantRepository.findByGenerationIdOrderByIdAsc(generationId);
    }
    private ShopifyListingTask find(String generationId) {
        return taskRepository.findById(generationId).orElseThrow(() -> new ListingNotFoundException(generationId));
    }
    private ShopifyAdminGateway gateway() {
        ShopifyAdminGateway value = gatewayProvider.getIfAvailable();
        if (value == null) throw new ListingConflictException("Shopify 尚未启用或配置不完整");
        return value;
    }
    private ShopifyStoreDomain domain() {
        try { return new ShopifyStoreDomain(properties.getShopDomain()); }
        catch (IllegalArgumentException error) { throw new ListingConflictException("Shopify 店铺域名配置无效"); }
    }
    private boolean validHttpsUrl(String value) {
        return value != null && value.startsWith("https://");
    }
    private void validateImages(List<String> images) {
        if (images == null || images.isEmpty() || images.stream().anyMatch(value -> !validHttpsUrl(value))) {
            throw new ListingConflictException("至少需要一张有效的 HTTPS 商品图片");
        }
        if (images.stream().distinct().count() != images.size()) {
            throw new ListingConflictException("商品图片地址不能重复");
        }
    }
    private String normalizeOption(String value) {
        String normalized = value == null || value.isBlank() ? "Default" : value.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
