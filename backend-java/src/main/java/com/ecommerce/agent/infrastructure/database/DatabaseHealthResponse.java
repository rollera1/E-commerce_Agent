package com.ecommerce.agent.infrastructure.database;

public record DatabaseHealthResponse(
        String status,
        String service,
        int probe) {
}
