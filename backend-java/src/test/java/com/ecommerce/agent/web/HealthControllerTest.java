package com.ecommerce.agent.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.agent.integration.python.PythonHealthClient;
import com.ecommerce.agent.integration.python.PythonHealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PythonHealthClient pythonHealthClient;

    @Test
    void returnsJavaHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("backend-java"));
    }

    @Test
    void returnsPythonHealthThroughJava() throws Exception {
        when(pythonHealthClient.checkHealth())
                .thenReturn(new PythonHealthResponse("ok", "ai-python", "0.1.0"));

        mockMvc.perform(get("/api/health/python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.caller").value("backend-java"))
                .andExpect(jsonPath("$.downstream.service").value("ai-python"));
    }
}
