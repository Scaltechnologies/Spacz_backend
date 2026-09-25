package com.spacz.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpaczUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaczUserServiceApplication.class, args);
    }
}
