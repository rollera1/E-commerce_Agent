package com.ecommerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Java 后端启动类。 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class EcommerceAgentApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 容器和 HTTP 服务。
        SpringApplication.run(EcommerceAgentApplication.class, args);
    }
}
