package nl.invokedynamic.demo.analytics.repository;

import nl.invokedynamic.demo.analytics.domain.WaitingListDailyMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitingListDailyMetricsRepository extends JpaRepository<WaitingListDailyMetricsEntity, UUID> {
    Optional<WaitingListDailyMetricsEntity> findByRestaurantIdAndMetricDate(UUID restaurantId, LocalDate metricDate);
    List<WaitingListDailyMetricsEntity> findByRestaurantId(UUID restaurantId);
}
