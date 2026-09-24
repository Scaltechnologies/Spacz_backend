package com.spacz.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpaczAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaczAdminServiceApplication.class, args);
    }
}
