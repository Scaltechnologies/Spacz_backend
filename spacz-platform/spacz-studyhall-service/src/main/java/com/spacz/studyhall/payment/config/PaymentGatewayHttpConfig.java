package com.spacz.studyhall.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Separate client for calls out to the payment gateway (public internet, not the private service
 * network): longer timeouts than the internal RestClients, since an order API round trip can
 * legitimately take longer. Injected by name (@Qualifier("razorpayRestTemplate")) only.
 * (jackson-dataformat-xml is not on this service's classpath, so JSON is the only converter that
 * applies to Map bodies; no XML-converter removal needed, unlike Strike.)
 */
@Configuration
public class PaymentGatewayHttpConfig {

    @Bean
    public RestTemplate razorpayRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
