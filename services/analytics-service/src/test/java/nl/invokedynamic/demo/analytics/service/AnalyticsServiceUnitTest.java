package nl.invokedynamic.demo.analytics.service;

import nl.invokedynamic.demo.analytics.domain.ReservationDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.domain.WaitingListDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.repository.ReservationDailyMetricsRepository;
import nl.invokedynamic.demo.analytics.repository.WaitingListDailyMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(resRepo, waitRepo);
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
    void shouldCalculateSummaryCorrectly() {
        UUID restId = UUID.randomUUID();
        ReservationDailyMetricsEntity res = new ReservationDailyMetricsEntity(UUID.randomUUID(), restId, LocalDate.now());
        res.incrementCreated(4);
        res.incrementCreated(2);
        res.incrementCancelled();

        WaitingListDailyMetricsEntity wait = new WaitingListDailyMetricsEntity(UUID.randomUUID(), restId, LocalDate.now());
        wait.incrementEntries();
        wait.incrementOffersAccepted();

        when(resRepo.findByRestaurantId(restId)).thenReturn(List.of(res));
        when(waitRepo.findByRestaurantId(restId)).thenReturn(List.of(wait));

        AnalyticsService.AnalyticsSummary summary = service.getSummary(restId);

        assertThat(summary.totalReservations()).isEqualTo(2);
        assertThat(summary.cancelledReservations()).isEqualTo(1);
        assertThat(summary.averagePartySize()).isEqualTo(3.0);
        assertThat(summary.waitingListEntries()).isEqualTo(1);
        assertThat(summary.waitingListConversions()).isEqualTo(1);
        assertThat(summary.waitingListConversionRate()).isEqualTo(100.0);
    }
}
