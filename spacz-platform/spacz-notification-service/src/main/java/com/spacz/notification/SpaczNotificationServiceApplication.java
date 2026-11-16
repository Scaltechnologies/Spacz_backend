package com.spacz.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpaczNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaczNotificationServiceApplication.class, args);
    }
}
