package com.ecommerce.agent.integration.python;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external-services.python")
public record PythonServiceProperties(URI baseUrl) {
}
