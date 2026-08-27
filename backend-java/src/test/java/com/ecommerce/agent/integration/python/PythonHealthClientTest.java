package com.ecommerce.agent.integration.python;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class PythonHealthClientTest {

    @Test
    void constructsWithoutAnAutoConfiguredRestClientBuilder() {
        var properties = new PythonServiceProperties(URI.create("http://localhost:8005"));

        var client = new PythonHealthClient(properties);

        assertThat(client).isNotNull();
    }
}
