package nl.invokedynamic.demo.analytics.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsCalculatorTest {

    @Test
    void shouldAccumulateReservationMetrics() {
        ReservationDailyMetricsEntity entity = new ReservationDailyMetricsEntity(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
        entity.incrementCreated(4);
        entity.incrementCreated(2);

        assertThat(entity.getReservationsCreatedCount()).isEqualTo(2);
        assertThat(entity.getTotalGuestsCount()).isEqualTo(6);
    }
}
