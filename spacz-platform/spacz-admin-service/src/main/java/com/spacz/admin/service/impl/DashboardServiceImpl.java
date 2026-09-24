package com.spacz.admin.service.impl;

import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.dto.AccountStatsDto;
import com.spacz.admin.client.dto.StudyHallStatsDto;
import com.spacz.admin.dto.DashboardResponse;
import com.spacz.admin.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Aggregates statistics from the owning services in parallel. A failing service degrades the
 * dashboard (its section is null) instead of failing it.
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final long TIMEOUT_SECONDS = 8;

    private final AuthServiceClient authClient;
    private final StudyHallServiceClient studyHallClient;
    private final Executor executor;
    private final Clock clock;

    public DashboardServiceImpl(AuthServiceClient authClient, StudyHallServiceClient studyHallClient,
                                @Qualifier("dashboardExecutor") Executor executor, Clock clock) {
        this.authClient = authClient;
        this.studyHallClient = studyHallClient;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public DashboardResponse dashboard() {
        CompletableFuture<AccountStatsDto> accounts = async(authClient::stats);
        CompletableFuture<StudyHallStatsDto> halls = async(studyHallClient::stats);

        List<String> unavailable = new ArrayList<>();
        AccountStatsDto a = await(accounts, "auth-service", unavailable);
        StudyHallStatsDto h = await(halls, "studyhall-service", unavailable);

        return new DashboardResponse(
                a == null ? null : new DashboardResponse.UserStats(a.totalUsers(), a.activeUsers(), a.suspendedUsers(),
                        a.totalVendorAccounts(), a.totalAdmins()),
                h == null ? null : new DashboardResponse.VendorStats(h.totalVendors(), h.draftVendors(),
                        h.pendingVendors(), h.approvedVendors() + h.activeVendors(), h.suspendedVendors(),
                        h.rejectedVendors()),
                h == null ? null : new DashboardResponse.StudyHallStats(h.totalStudyHalls(), h.activeStudyHalls(),
                        h.pendingStudyHalls(), h.suspendedStudyHalls(), h.totalSeats(), h.activePrograms()),
                h == null ? null : new DashboardResponse.BookingStats(h.totalBookings(), h.todaysBookings(),
                        h.occupiedSeatsToday(), h.pendingBookings(), h.confirmedBookings(), h.activeEnrollments()),
                unavailable,
                clock.instant());
    }

    private <T> CompletableFuture<T> async(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, executor);
    }

    private static <T> T await(CompletableFuture<T> future, String service, List<String> unavailable) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            unavailable.add(service);
            return null;
        } catch (Exception ex) {
            log.warn("Dashboard: {} unavailable: {}", service, ex.getMessage());
            future.cancel(true);
            unavailable.add(service);
            return null;
        }
    }
}
