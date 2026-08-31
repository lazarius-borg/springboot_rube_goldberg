package nl.invokedynamic.demo.analytics.repository;

import nl.invokedynamic.demo.analytics.domain.ReservationDailyMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationDailyMetricsRepository extends JpaRepository<ReservationDailyMetricsEntity, UUID> {
    Optional<ReservationDailyMetricsEntity> findByRestaurantIdAndMetricDate(UUID restaurantId, LocalDate metricDate);
    List<ReservationDailyMetricsEntity> findByRestaurantId(UUID restaurantId);
}
