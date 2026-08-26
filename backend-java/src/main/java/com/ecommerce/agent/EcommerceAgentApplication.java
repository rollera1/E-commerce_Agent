package com.ecommerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EcommerceAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceAgentApplication.class, args);
    }
}
