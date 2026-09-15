package nl.invokedynamic.demo.analytics.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import nl.invokedynamic.demo.analytics.domain.ReservationDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.domain.ReservationHourlyMetricsEntity;
import nl.invokedynamic.demo.analytics.domain.WaitingListDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.repository.ReservationDailyMetricsRepository;
import nl.invokedynamic.demo.analytics.repository.ReservationHourlyMetricsRepository;
import nl.invokedynamic.demo.analytics.repository.WaitingListDailyMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceUnitTest {

    @Mock private ReservationDailyMetricsRepository resRepo;
    @Mock private WaitingListDailyMetricsRepository waitRepo;
    @Mock private ReservationHourlyMetricsRepository hourlyRepo;

    private MeterRegistry meterRegistry;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new AnalyticsService(resRepo, waitRepo, hourlyRepo, meterRegistry);
    }

    @Test
    void shouldRecordReservationCreated() {
        UUID restId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        when(resRepo.findByRestaurantIdAndMetricDate(restId, date)).thenReturn(Optional.empty());

        service.recordReservationCreated(restId, date, 4);

        verify(resRepo).save(any(ReservationDailyMetricsEntity.class));
    }

    @Test
    void shouldRecordHourlyDemandAndPartySizeOnReservationCreated() {
        UUID restId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-09-18T19:30:00Z"); // Friday 19:30 UTC
        LocalDate date = startTime.atZone(ZoneOffset.UTC).toLocalDate();

        when(resRepo.findByRestaurantIdAndMetricDate(restId, date)).thenReturn(Optional.empty());
        when(hourlyRepo.findByRestaurantIdAndMetricDateAndHourOfDay(restId, date, 19)).thenReturn(Optional.empty());

        service.recordReservationCreated(restId, startTime, 8); // 8 guests -> 7+ bucket

        verify(resRepo).save(any(ReservationDailyMetricsEntity.class));
        verify(hourlyRepo).save(any(ReservationHourlyMetricsEntity.class));

        assertThat(meterRegistry.find("reservation_demand_total")
                .tag("restaurant_id", restId.toString())
                .tag("day_of_week", "FRIDAY")
                .tag("hour", "19")
                .counter()).isNotNull();

        assertThat(meterRegistry.find("reservation_party_size_total")
                .tag("restaurant_id", restId.toString())
                .tag("party_bucket", "7+")
                .counter()).isNotNull();
    }

    @Test
    void shouldCategorizePartySizesAndCancellationsInSummary() {
        UUID restId = UUID.randomUUID();
        ReservationDailyMetricsEntity res = new ReservationDailyMetricsEntity(UUID.randomUUID(), restId, LocalDate.now());
        res.incrementCreated(2);
        res.incrementCreated(4);
        res.incrementCreated(8); // 7+
        res.incrementCancelled("CUSTOMER_REQUEST");
        res.incrementCancelled("NO_SHOW_LATE_CANCEL");

        WaitingListDailyMetricsEntity wait = new WaitingListDailyMetricsEntity(UUID.randomUUID(), restId, LocalDate.now());
        wait.incrementEntries();
        wait.incrementOffersAccepted();

        when(resRepo.findByRestaurantId(restId)).thenReturn(List.of(res));
        when(waitRepo.findByRestaurantId(restId)).thenReturn(List.of(wait));

        AnalyticsService.AnalyticsSummary summary = service.getSummary(restId);

        assertThat(summary.totalReservations()).isEqualTo(3);
        assertThat(summary.cancelledReservations()).isEqualTo(2);
        assertThat(summary.cancellationRatePercentage()).isEqualTo((2.0 / 3.0) * 100.0);

        assertThat(summary.cancellationsByCategory())
                .containsEntry("CUSTOMER_REQUEST", 1L)
                .containsEntry("NO_SHOW_LATE_CANCEL", 1L)
                .containsEntry("RESTAURANT_INITIATED", 0L);

        assertThat(summary.partySizeDistribution())
                .containsEntry("1", 0L)
                .containsEntry("2", 1L)
                .containsEntry("3", 0L)
                .containsEntry("4", 1L)
                .containsEntry("5", 0L)
                .containsEntry("6", 0L)
                .containsEntry("7+", 1L);

        assertThat(summary.waitingListConversionRate()).isEqualTo(100.0);
    }

    @Test
    void shouldHandleNonExistentRestaurantGracefullyWithoutZeroDivision() {
        UUID nonExistentId = UUID.randomUUID();
        when(resRepo.findByRestaurantId(nonExistentId)).thenReturn(List.of());
        when(waitRepo.findByRestaurantId(nonExistentId)).thenReturn(List.of());

        AnalyticsService.AnalyticsSummary summary = service.getSummary(nonExistentId);

        assertThat(summary.totalReservations()).isEqualTo(0);
        assertThat(summary.cancelledReservations()).isEqualTo(0);
        assertThat(summary.cancellationRatePercentage()).isEqualTo(0.0);
        assertThat(summary.waitingListConversionRate()).isEqualTo(0.0);
        assertThat(summary.cancellationsByCategory()).containsEntry("CUSTOMER_REQUEST", 0L);
        assertThat(summary.partySizeDistribution()).containsEntry("2", 0L);
    }
}
