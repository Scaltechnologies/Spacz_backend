package com.spacz.studyhall;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentRequests.VerifyPaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentResponse;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentMode;
import com.spacz.studyhall.payment.gateway.CreateOrderCommand;
import com.spacz.studyhall.payment.gateway.GatewayOrder;
import com.spacz.studyhall.payment.gateway.GatewayPaymentState;
import com.spacz.studyhall.payment.gateway.GatewayStatusResult;
import com.spacz.studyhall.payment.gateway.PaymentGateway;
import com.spacz.studyhall.payment.gateway.PaymentModeConfigClient;
import com.spacz.studyhall.payment.gateway.WebhookVerificationResult;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.security.Role;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.StudyHallAdminService;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Payment concurrency guarantees on a real PostgreSQL 16 (row locks, partial unique indexes), with
 * Flyway V12 applied and the JPA mapping validated against it. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spacz.security.jwt.secret=test-only-jwt-secret-for-automated-tests-0123456789",
        "spacz.security.internal.api-key=test-internal-api-key-0123456789",
        "spacz.booking.lifecycle-initial-delay-ms=3600000",
        "spacz.payment.jobs.initial-delay-ms=3600000",
        "spacz.notifications.enabled=false"
})
class PaymentRacePostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicLong IDS = new AtomicLong(990_000);

    @Autowired
    private VendorService vendorService;
    @Autowired
    private StudyHallManagementService hallService;
    @Autowired
    private SeatingService seatingService;
    @Autowired
    private StudyHallAdminService adminService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private PaymentGateway gateway;
    @MockitoBean
    private PaymentModeConfigClient modeClient;

    @BeforeEach
    void stubs() {
        when(modeClient.getCurrentMode()).thenReturn(PaymentMode.BOTH);
        when(gateway.getProviderName()).thenReturn("RAZORPAY");
        when(gateway.getPublicKeyId()).thenReturn("rzp_test_public");
        when(gateway.createOrder(any(CreateOrderCommand.class))).thenAnswer(inv -> GatewayOrder.builder()
                .gatewayOrderId("order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)).build());
        when(gateway.verifyCheckoutSignature(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void verifyAndWebhookRacingOnOneCaptureConfirmTheBookingExactlyOnce() throws Exception {
        long vendorId = IDS.incrementAndGet();
        long[] hallSeat = liveHallWithOneSeat(vendorId);
        long student = IDS.incrementAndGet();
        long bookingId = book(student, hallSeat, 3);
        PaymentResponse payment = paymentService.createPayment(student, new CreatePaymentRequest(bookingId, null), null);
        String orderId = payment.gatewayOrderId();
        GatewayStatusResult captured = GatewayStatusResult.builder().state(GatewayPaymentState.SUCCESS)
                .gatewayOrderId(orderId).gatewayPaymentId("pay_race").amount(payment.amount()).build();
        when(gateway.fetchOrderStatus(orderId)).thenReturn(captured);

        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int n = i;
            if (i % 2 == 0) {
                calls.add(() -> paymentService.verifyPayment(payment.paymentId(), student,
                        new VerifyPaymentRequest(orderId, "pay_race", "sig")));
            } else {
                String raw = "{\"id\":\"evt_race_" + n + "\"}";
                when(gateway.verifyAndParseWebhook(eq(raw), eq("sig"))).thenReturn(WebhookVerificationResult.builder()
                        .signatureValid(true).gatewayEventId("evt_race_" + n).eventType("payment.captured")
                        .status(captured).rawJson(raw).build());
                calls.add(() -> {
                    paymentService.handleWebhook(raw, "sig");
                    return null;
                });
            }
        }
        runConcurrently(calls);

        assertThat(jdbc.queryForObject("select status from payments where id = ?", String.class, payment.paymentId()))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId))
                .isEqualTo("CONFIRMED");
        assertThat(count("select count(*) from payment_audit_events where payment_id = ? and action = 'BOOKING_CONFIRMED'",
                payment.paymentId())).isEqualTo(1);
        assertThat(count("select count(*) from enrollments where user_id = ? and study_hall_id = ?", student, hallSeat[0]))
                .isEqualTo(1);
    }

    @Test
    void twoVendorDevicesConfirmingTheSamePayAtHallOrderConfirmOnce() throws Exception {
        long vendorId = IDS.incrementAndGet();
        long[] hallSeat = liveHallWithOneSeat(vendorId);
        long student = IDS.incrementAndGet();
        long bookingId = book(student, hallSeat, 4);
        PaymentResponse payment = paymentService.createPayment(student,
                new CreatePaymentRequest(bookingId, PaymentMethod.PAY_AT_HALL), null);
        paymentService.verifyQr(vendorId, payment.qrPayload());

        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(() -> paymentService.confirmPayAtHall(vendorId, payment.paymentId()));
        }
        List<Object> results = runConcurrently(calls);

        assertThat(results).allSatisfy(r -> assertThat(r).isNotInstanceOf(Throwable.class));
        assertThat(count("select count(*) from payment_audit_events where payment_id = ? and action = 'BOOKING_CONFIRMED'",
                payment.paymentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void concurrentCreatesForOneBookingLeaveExactlyOneOpenPayment() throws Exception {
        long vendorId = IDS.incrementAndGet();
        long[] hallSeat = liveHallWithOneSeat(vendorId);
        long student = IDS.incrementAndGet();
        long bookingId = book(student, hallSeat, 5);

        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> paymentService.createPayment(student, new CreatePaymentRequest(bookingId, null),
                    "race-" + UUID.randomUUID()));
        }
        List<Object> results = runConcurrently(calls);

        // Losers either got the winner's payment back or a unique-index violation (409 at the API).
        assertThat(results).allSatisfy(r -> assertThat(r).satisfiesAnyOf(
                x -> assertThat(x).isInstanceOf(PaymentResponse.class),
                x -> assertThat(x).isInstanceOf(DataIntegrityViolationException.class)));
        assertThat(count("select count(*) from payments where booking_id = ? and status in "
                + "('CREATED','PENDING','PROCESSING','AWAITING_VENDOR_CONFIRMATION')", bookingId)).isEqualTo(1);
    }

    @Test
    void partialUniqueIndexesHoldEvenWhenTheServiceIsBypassed() {
        long vendorId = IDS.incrementAndGet();
        long[] hallSeat = liveHallWithOneSeat(vendorId);
        long student = IDS.incrementAndGet();
        long bookingId = book(student, hallSeat, 6);
        String insert = """
                insert into payments (user_id, booking_id, study_hall_id, seat_id, vendor_id, booking_reference, description,
                                      final_amount, status, payment_method, expires_at)
                values (?, ?, ?, ?, ?, 'SPZ-X', 'x', 100, ?, 'ONLINE', now())""";
        jdbc.update(insert, student, bookingId, hallSeat[0], hallSeat[1], vendorId, "SUCCESS");
        assertThatThrownBy(() -> jdbc.update(insert, student, bookingId, hallSeat[0], hallSeat[1], vendorId, "SUCCESS"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, student, bookingId, hallSeat[0], hallSeat[1], vendorId, "PENDING");
        assertThatThrownBy(() -> jdbc.update(insert, student, bookingId, hallSeat[0], hallSeat[1], vendorId, "PROCESSING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, student, bookingId, hallSeat[0], hallSeat[1], vendorId, "FAILED");
        jdbc.update(insert, student, bookingId, hallSeat[0], hallSeat[1], vendorId, "FAILED");
    }

    private List<Object> runConcurrently(List<Callable<Object>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> call : calls) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return call.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private long book(long student, long[] hallSeat, int daysAhead) {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(daysAhead);
        return bookingService.create(student, new CreateBookingRequest(hallSeat[0], hallSeat[1], BookingPlan.DAILY,
                day, day, null, null)).id();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** {hallId, seatId} */
    private long[] liveHallWithOneSeat(long vendorId) {
        AuthenticatedUser vendor = new AuthenticatedUser(vendorId, "v" + vendorId + "@test.local", Role.VENDOR);
        vendorService.createFromRegistration(new CreateVendorProfileRequest(vendorId, vendor.email(), null, null, null, null));
        vendorService.updateMine(vendor, new VendorProfileRequest("Race Hall", "Owner", "+919999999999", null,
                "Road", "Hyderabad", null, null, null, null, null));
        vendorService.submit(vendor);
        vendorService.applyAction(vendorId, StatusAction.APPROVE, null);
        long hallId = hallService.create(vendor, new CreateStudyHallRequest("Hall " + vendorId, null, "Road", "Hyderabad",
                "Telangana", null, null, null, null, null, new BigDecimal("120.00"), null, null,
                LocalTime.of(6, 0), LocalTime.of(22, 0))).id();
        long seatId = seatingService.createBlock(vendorId, hallId,
                new CreateBlockRequest("Solo", 1, 1, null, null, null, null, null, null, null, null)).seats().get(0).id();
        hallService.submitForApproval(vendor, hallId);
        adminService.applyAction(hallId, StatusAction.APPROVE, null);
        return new long[]{hallId, seatId};
    }
}
