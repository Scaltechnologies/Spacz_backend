package com.spacz.studyhall.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.studyhall.audit.AuditEvent;
import com.spacz.studyhall.config.ServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * → admin-service (audit log ingestion).
 */
@Component
public class AdminServiceClient extends BaseServiceClient {

    private final RestClient restClient;

    public AdminServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                              @Value("${spacz.clients.admin-service-url}") String baseUrl) {
        super("admin-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    public void recordAuditEvent(AuditEvent event) {
        run(() -> restClient.post()
                .uri("/internal/audit-events")
                .body(event)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .toBodilessEntity());
    }
}
