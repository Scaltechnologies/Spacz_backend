package com.spacz.auth.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.auth.client.dto.CreateVendorProfileRequest;
import com.spacz.auth.config.ServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * auth-service → studyhall-service.
 */
@Component
public class StudyHallServiceClient extends BaseServiceClient {

    private final RestClient restClient;

    public StudyHallServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                                  @Value("${spacz.clients.studyhall-service-url}") String baseUrl) {
        super("studyhall-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    /** Idempotent on vendorId. */
    public void createVendorProfile(CreateVendorProfileRequest request) {
        run(() -> restClient.post()
                .uri("/internal/vendors")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .toBodilessEntity());
    }
}
