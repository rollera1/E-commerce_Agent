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
        String status = "ok".equals(python.status()) ? "ok" : "degraded";
        return ResponseEntity.ok(new IntegrationHealthResponse(status, "backend-java", python));
    }

    @GetMapping("/database")
    public DatabaseHealthResponse databaseHealth() {
        return databaseHealthService.checkHealth();
    }

    public record IntegrationHealthResponse(
            String status,
            String caller,
            PythonHealthResponse downstream) {
    }
}
