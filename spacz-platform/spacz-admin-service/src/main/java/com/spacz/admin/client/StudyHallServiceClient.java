package com.spacz.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.client.dto.BookingDto;
import com.spacz.admin.client.dto.StatusActionDto;
import com.spacz.admin.client.dto.StudyHallDetailDto;
import com.spacz.admin.client.dto.StudyHallStatsDto;
import com.spacz.admin.client.dto.StudyHallSummaryDto;
import com.spacz.admin.config.ServiceClientFactory;
import com.spacz.admin.dto.PageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * admin-service → studyhall-service (/internal/vendors, /internal/studyhalls, /internal/bookings ...).
 */
@Component
public class StudyHallServiceClient extends BaseServiceClient {

    private static final ParameterizedTypeReference<PageResponse<AdminVendorDto>> VENDOR_PAGE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<PageResponse<StudyHallSummaryDto>> HALL_PAGE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<PageResponse<BookingDto>> BOOKING_PAGE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public StudyHallServiceClient(ServiceClientFactory factory, ObjectMapper objectMapper,
                                  @Value("${spacz.clients.studyhall-service-url}") String baseUrl) {
        super("studyhall-service", objectMapper);
        this.restClient = factory.create(baseUrl);
    }

    public PageResponse<AdminVendorDto> searchVendors(String search, String status, String city, Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> QueryParams.page(uri.path("/internal/vendors"), pageable)
                        .queryParamIfPresent("search", QueryParams.optional(search))
                        .queryParamIfPresent("status", QueryParams.optional(status))
                        .queryParamIfPresent("city", QueryParams.optional(city))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(VENDOR_PAGE));
    }

    public AdminVendorDto getVendor(Long vendorId) {
        return execute(() -> restClient.get()
                .uri("/internal/vendors/{vendorId}", vendorId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(AdminVendorDto.class));
    }

    public AdminVendorDto changeVendorStatus(Long vendorId, StatusActionDto action) {
        return execute(() -> restClient.patch()
                .uri("/internal/vendors/{vendorId}/status", vendorId)
                .body(action)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(AdminVendorDto.class));
    }

    public PageResponse<StudyHallSummaryDto> searchStudyHalls(String search, String status, String city, Long vendorId,
                                                              Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> QueryParams.page(uri.path("/internal/studyhalls"), pageable)
                        .queryParamIfPresent("search", QueryParams.optional(search))
                        .queryParamIfPresent("status", QueryParams.optional(status))
                        .queryParamIfPresent("city", QueryParams.optional(city))
                        .queryParamIfPresent("vendorId", QueryParams.optional(vendorId))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(HALL_PAGE));
    }

    public StudyHallDetailDto getStudyHall(Long id) {
        return execute(() -> restClient.get()
                .uri("/internal/studyhalls/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(StudyHallDetailDto.class));
    }

    public StudyHallDetailDto changeStudyHallStatus(Long id, StatusActionDto action) {
        return execute(() -> restClient.patch()
                .uri("/internal/studyhalls/{id}/status", id)
                .body(action)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(StudyHallDetailDto.class));
    }

    public PageResponse<BookingDto> searchBookings(Long studyHallId, Long userId, String status, LocalDate from,
                                                   LocalDate to, Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> QueryParams.page(uri.path("/internal/bookings"), pageable)
                        .queryParamIfPresent("studyHallId", QueryParams.optional(studyHallId))
                        .queryParamIfPresent("userId", QueryParams.optional(userId))
                        .queryParamIfPresent("status", QueryParams.optional(status))
                        .queryParamIfPresent("from", QueryParams.optional(from))
                        .queryParamIfPresent("to", QueryParams.optional(to))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(BOOKING_PAGE));
    }

    /** A student's hall memberships, passed through. */
    public JsonNode enrollments(Long userId) {
        return execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/enrollments").queryParam("userId", userId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    public StudyHallStatsDto stats() {
        return execute(() -> restClient.get()
                .uri("/internal/stats")
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(StudyHallStatsDto.class));
    }
}
