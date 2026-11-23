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
import java.util.Map;

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

    /** Admin kill switch for Pay at Hall on one vendor's halls. Returns {vendorId, payAtHallEnabled}. */
    public JsonNode setPayAtHall(Long vendorId, boolean enabled) {
        return execute(() -> restClient.patch()
                .uri("/internal/vendors/{vendorId}/pay-at-hall", vendorId)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** Append-only audit trail of one payment, passed through. */
    public JsonNode paymentAuditEvents(Long paymentId) {
        return execute(() -> restClient.get()
                .uri("/internal/payments/{paymentId}/audit-events", paymentId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** Every payment of one booking (newest first), passed through. */
    public JsonNode paymentsForBooking(Long bookingId) {
        return execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/payments").queryParam("bookingId", bookingId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** Ledger transactions (payments received, refunds), filtered and paged, passed through. */
    public JsonNode transactions(TransactionQuery q, Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> q.apply(QueryParams.page(uri.path("/internal/transactions"), pageable)).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** Ledger totals over the same filters, passed through. */
    public JsonNode transactionSummary(TransactionQuery q) {
        return execute(() -> restClient.get()
                .uri(uri -> q.apply(uri.path("/internal/transactions/summary")).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** A booking's status history, payments and transactions, passed through. */
    public JsonNode bookingHistory(Long bookingId) {
        return execute(() -> restClient.get()
                .uri("/internal/bookings/{bookingId}/history", bookingId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** Optional ledger filters; null fields are left out of the query string. */
    public record TransactionQuery(Long userId, Long vendorId, Long studyHallId, Long bookingId, String type,
                                   String status, String paymentMethod, LocalDate from, LocalDate to) {

        org.springframework.web.util.UriBuilder apply(org.springframework.web.util.UriBuilder uri) {
            return uri.queryParamIfPresent("userId", QueryParams.optional(userId))
                    .queryParamIfPresent("vendorId", QueryParams.optional(vendorId))
                    .queryParamIfPresent("studyHallId", QueryParams.optional(studyHallId))
                    .queryParamIfPresent("bookingId", QueryParams.optional(bookingId))
                    .queryParamIfPresent("type", QueryParams.optional(type))
                    .queryParamIfPresent("status", QueryParams.optional(status))
                    .queryParamIfPresent("paymentMethod", QueryParams.optional(paymentMethod))
                    .queryParamIfPresent("from", QueryParams.optional(from))
                    .queryParamIfPresent("to", QueryParams.optional(to));
        }
    }

    // ── vendor wallets and withdrawals (passed through) ──

    public JsonNode withdrawals(String status, Long vendorId, Pageable pageable) {
        return execute(() -> restClient.get()
                .uri(uri -> QueryParams.page(uri.path("/internal/withdrawals"), pageable)
                        .queryParamIfPresent("status", QueryParams.optional(status))
                        .queryParamIfPresent("vendorId", QueryParams.optional(vendorId))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    public JsonNode withdrawal(Long withdrawalId) {
        return execute(() -> restClient.get()
                .uri("/internal/withdrawals/{id}", withdrawalId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    /** action: processing | paid | reject. */
    public JsonNode withdrawalAction(Long withdrawalId, String action, Map<String, Object> body) {
        return execute(() -> restClient.post()
                .uri("/internal/withdrawals/{id}/{action}", withdrawalId, action)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    public JsonNode vendorWallet(Long vendorId) {
        return execute(() -> restClient.get()
                .uri("/internal/vendors/{vendorId}/wallet", vendorId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler())
                .body(JsonNode.class));
    }

    public JsonNode vendorWalletEntries(Long vendorId, String type, int page, int size) {
        return execute(() -> restClient.get()
                .uri(uri -> uri.path("/internal/vendors/{vendorId}/wallet/entries")
                        .queryParamIfPresent("type", QueryParams.optional(type))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(vendorId))
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
