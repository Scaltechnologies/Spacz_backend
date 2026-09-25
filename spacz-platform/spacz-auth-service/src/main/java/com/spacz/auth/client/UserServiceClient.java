package com.spacz.auth.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.auth.client.dto.CreateUserProfileRequest;
import com.spacz.auth.config.ServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * auth-service → user-service.
 */
@Component
public class UserServiceClient extends BaseServiceClient {

    private final RestClient restClient;

    public UserServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                             @Value("${spacz.clients.user-service-url}") String baseUrl) {
        super("user-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    /** Idempotent on userId. */
    public void createProfile(CreateUserProfileRequest request) {
        run(() -> restClient.post()
                .uri("/internal/users")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .toBodilessEntity());
    }
}
