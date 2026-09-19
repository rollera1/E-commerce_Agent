package com.ecommerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Java 后端启动类。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EcommerceAgentApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 容器和 HTTP 服务。
        SpringApplication.run(EcommerceAgentApplication.class, args);
    }
}
