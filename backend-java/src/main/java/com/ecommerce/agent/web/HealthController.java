package com.ecommerce.agent.web;

import com.ecommerce.agent.infrastructure.database.DatabaseHealthResponse;
import com.ecommerce.agent.infrastructure.database.DatabaseHealthService;
import com.ecommerce.agent.integration.python.PythonHealthClient;
import com.ecommerce.agent.integration.python.PythonHealthResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供 Java、MySQL 和 Python 的健康检查接口。 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final PythonHealthClient pythonHealthClient;
    private final DatabaseHealthService databaseHealthService;

    public HealthController(
            PythonHealthClient pythonHealthClient,
            DatabaseHealthService databaseHealthService) {
        this.pythonHealthClient = pythonHealthClient;
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "backend-java",
                "version", "0.1.0");
    }

    @GetMapping("/python")
    public ResponseEntity<IntegrationHealthResponse> pythonHealth() {
        PythonHealthResponse python = pythonHealthClient.checkHealth();
        // Python 可访问但自身状态异常时，Java 对外报告 degraded。
        String status = "ok".equals(python.status()) ? "ok" : "degraded";
        return ResponseEntity.ok(new IntegrationHealthResponse(status, "backend-java", python));
    }

    @GetMapping("/database")
    public DatabaseHealthResponse databaseHealth() {
        return databaseHealthService.checkHealth();
    }

    /** Java 调用 Python 后组合得到的健康状态。 */
    public record IntegrationHealthResponse(
            String status,
            String caller,
            PythonHealthResponse downstream) {
    }
}
