package nl.invokedynamic.demo.analytics.repository;

import nl.invokedynamic.demo.analytics.domain.ReservationHourlyMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationHourlyMetricsRepository extends JpaRepository<ReservationHourlyMetricsEntity, UUID> {
    Optional<ReservationHourlyMetricsEntity> findByRestaurantIdAndMetricDateAndHourOfDay(UUID restaurantId, LocalDate metricDate, int hourOfDay);
    List<ReservationHourlyMetricsEntity> findByRestaurantId(UUID restaurantId);
}
