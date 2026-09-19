package com.ecommerce.agent.integration.python;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 中 external-services.python 配置的映射对象。 */
@ConfigurationProperties(prefix = "external-services.python")
public record PythonServiceProperties(
        // Python 服务根地址，例如 http://localhost:8005。
        URI baseUrl) {
}
