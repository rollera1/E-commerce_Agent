package com.ecommerce.agent.copyseo;

import com.ecommerce.agent.shopifylisting.ReviewMode;
import com.ecommerce.agent.shopifylisting.ShopifyListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 由 Java 定时推进 MySQL 中尚未完成的 Copy/SEO 业务任务。 */
@Component
@ConditionalOnProperty(name = "agent.execution-mode", havingValue = "async")
public class CopySeoAgentRunPoller {
    private static final Logger logger = LoggerFactory.getLogger(CopySeoAgentRunPoller.class);
    private final CopySeoGenerationService generationService;
    private final ShopifyListingService listingService;

    public CopySeoAgentRunPoller(
            CopySeoGenerationService generationService,
            ShopifyListingService listingService) {
        this.generationService = generationService;
        this.listingService = listingService;
    }

    @Scheduled(fixedDelayString = "${agent.poll-delay-ms:2000}")
    public void poll() {
        for (String generationId : generationService.pendingGenerationIds()) {
            try {
                CopySeoGenerationView generation = generationService.poll(generationId);
                continueAutomaticListing(generation);
            } catch (RuntimeException ignored) {
                // 不记录上游响应或异常正文，下一轮会继续处理持久化任务。
                logger.warn("Copy/SEO Agent 任务本轮推进失败: {}", generationId);
            }
        }
    }

    private void continueAutomaticListing(CopySeoGenerationView generation) {
        if (generation.status() != GenerationStatus.SUCCEEDED
                || !listingService.isConfigured(generation.generationId())) {
            return;
        }
        var listing = listingService.get(generation.generationId());
        if (listing.reviewMode() == ReviewMode.AUTO
                && generation.reviewStatus() == ReviewStatus.PENDING_REVIEW) {
            generationService.accept(generation.generationId());
            listingService.processAccepted(generation.generationId());
        }
    }
}
