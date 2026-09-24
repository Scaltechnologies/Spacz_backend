package com.spacz.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.admin.client.dto.UserProfileDto;
import com.spacz.admin.config.ServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * admin-service → user-service (/internal/users).
 */
@Component
public class UserServiceClient extends BaseServiceClient {

    private final RestClient restClient;

    public UserServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                             @Value("${spacz.clients.user-service-url}") String baseUrl) {
        super("user-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    public UserProfileDto getProfile(Long userId) {
        return execute(() -> restClient.get()
                .uri("/internal/users/{userId}", userId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(UserProfileDto.class));
    }

    /** A student's exam / course choices, passed through. */
    public JsonNode programs(Long userId) {
        return execute(() -> restClient.get()
                .uri("/internal/users/{userId}/programs", userId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** A student's activity history page, passed through. */
    public JsonNode activity(Long userId, Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> QueryParams.page(uri.path("/internal/users/{userId}/activity"), pageable).build(userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }
}
