package com.spacz.studyhall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class SpaczStudyHallServiceApplication {

    public static void main(String[] args) {
        // hibernate.jdbc.time_zone=UTC converts java.time values through the JVM default zone. Wall-clock
        // LocalTime columns (operating hours) must not shift, so pin the JVM to UTC as the production
        // containers already are. Business dates use the explicit spacz.booking zone, not this default.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SpaczStudyHallServiceApplication.class, args);
    }
}
