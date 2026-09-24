package com.spacz.admin.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.client.dto.AccountStatsDto;
import com.spacz.admin.config.ServiceClientFactory;
import com.spacz.admin.dto.PageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * admin-service → auth-service (/internal/accounts).
 */
@Component
public class AuthServiceClient extends BaseServiceClient {

    private static final ParameterizedTypeReference<PageResponse<AccountDto>> ACCOUNT_PAGE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public AuthServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                             @Value("${spacz.clients.auth-service-url}") String baseUrl) {
        super("auth-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    public PageResponse<AccountDto> searchAccounts(String search, String role, String status, Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> QueryParams.page(uri.path("/internal/accounts"), pageable)
                        .queryParamIfPresent("search", QueryParams.optional(search))
                        .queryParamIfPresent("role", QueryParams.optional(role))
                        .queryParamIfPresent("status", QueryParams.optional(status))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(ACCOUNT_PAGE));
    }

    public AccountDto getAccount(Long accountId) {
        return execute(() -> restClient.get()
                .uri("/internal/accounts/{id}", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(AccountDto.class));
    }

    public AccountDto updateStatus(Long accountId, String status) {
        return execute(() -> restClient.patch()
                .uri("/internal/accounts/{id}/status", accountId)
                .body(Map.of("status", status))
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(AccountDto.class));
    }

    public AccountStatsDto stats() {
        return execute(() -> restClient.get()
                .uri("/internal/accounts/stats")
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(AccountStatsDto.class));
    }
}
