package com.ecommerce.agent.integration.python;

import com.ecommerce.agent.productselection.AgentProductSelection;
import com.ecommerce.agent.productselection.ProductSelectionAgentGateway;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 通过 HTTP 调用 Python 选品 Agent。 */
@Component
public class PythonProductSelectionAgentClient implements ProductSelectionAgentGateway {
    private final RestClient restClient;

    @Autowired
    public PythonProductSelectionAgentClient(PythonServiceProperties properties) {
        this(RestClient.builder().baseUrl(properties.baseUrl().toString()).build());
    }

    PythonProductSelectionAgentClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AgentProductSelection search(String query, int limit) {
        return restClient.post()
                .uri("/internal/agents/product-selection/searches")
                .body(Map.of("query", query, "limit", limit))
                .retrieve()
                .body(AgentProductSelection.class);
    }
}
