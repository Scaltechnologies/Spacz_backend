package com.spacz.studyhall.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.studyhall.client.dto.RecordActivityRequest;
import com.spacz.studyhall.client.dto.UserSummaryDto;
import com.spacz.studyhall.config.ServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;

/**
 * studyhall-service → user-service: student details for vendors (read-only) and activity events.
 */
@Component
public class UserServiceClient extends BaseServiceClient {

    private static final ParameterizedTypeReference<List<UserSummaryDto>> USER_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public UserServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                             @Value("${spacz.clients.user-service-url}") String baseUrl) {
        super("user-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    /** Unknown IDs are simply absent from the result. */
    public List<UserSummaryDto> findUsers(Collection<Long> userIds) {
        return execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/users").queryParam("ids", userIds.toArray()).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(USER_LIST));
    }

    public void recordActivity(Long userId, RecordActivityRequest request) {
        run(() -> restClient.post()
                .uri("/internal/users/{userId}/activities", userId)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .toBodilessEntity());
    }
}
