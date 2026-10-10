package com.ecommerce.agent.integration.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonProductSelectionAgentClientTest {

    @Test
    void callsPythonSelectionAgentAndMapsCandidates() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8005");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://localhost:8005/internal/agents/product-selection/searches"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"query":"宠物牵引绳","limit":10}
                        """))
                .andRespond(withSuccess("""
                        {
                          "selectionId":"selection-1",
                          "query":"宠物牵引绳",
                          "matchedQuery":"pet leash",
                          "outcome":"CANDIDATES_FOUND",
                          "candidates":[{
                            "sourceProductId":"cj-1",
                            "source":"CJ",
                            "title":"Pet Leash",
                            "description":"A leash",
                            "images":[],
                            "price":{"amount":9.99,"currency":"USD"},
                            "variants":[],
                            "inventoryStatus":"IN_STOCK",
                            "inventoryTotal":123,
                            "sourceUrl":"https://example.invalid/cj-1",
                            "fetchedAt":"2026-09-28T08:00:00Z",
                            "matchedQuery":"pet leash"
                          }],
                          "trace":{"searchCount":1}
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = new PythonProductSelectionAgentClient(builder.build())
                .search("宠物牵引绳", 10);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().sourceProductId()).isEqualTo("cj-1");
        assertThat(result.candidates().getFirst().inventoryTotal()).isEqualTo(123);
        server.verify();
    }
}
