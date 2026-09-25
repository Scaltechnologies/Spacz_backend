package com.spacz.user.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.user.client.dto.EnrollmentDto;
import com.spacz.user.client.dto.ProgramDto;
import com.spacz.user.config.ServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;

/**
 * user-service → studyhall-service (read-only): the program catalog and a student's hall memberships.
 */
@Component
public class StudyHallServiceClient extends BaseServiceClient {

    private static final ParameterizedTypeReference<List<ProgramDto>> PROGRAMS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<EnrollmentDto>> ENROLLMENTS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public StudyHallServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                                  @Value("${spacz.clients.studyhall-service-url}") String baseUrl) {
        super("studyhall-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    /** Unknown IDs are absent from the result. */
    public List<ProgramDto> findPrograms(Collection<Long> ids) {
        return execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/programs/lookup").queryParam("ids", ids.toArray()).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(PROGRAMS));
    }

    public List<EnrollmentDto> enrollments(Long userId) {
        return execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/enrollments").queryParam("userId", userId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(ENROLLMENTS));
    }
}
