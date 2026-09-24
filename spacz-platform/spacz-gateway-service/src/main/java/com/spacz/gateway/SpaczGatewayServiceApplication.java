package com.spacz.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpaczGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaczGatewayServiceApplication.class, args);
    }
}
