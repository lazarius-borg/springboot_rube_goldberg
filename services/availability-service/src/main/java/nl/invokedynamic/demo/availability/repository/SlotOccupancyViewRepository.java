package nl.invokedynamic.demo.availability.repository;

import nl.invokedynamic.demo.availability.domain.SlotOccupancyViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SlotOccupancyViewRepository extends JpaRepository<SlotOccupancyViewEntity, UUID> {
    List<SlotOccupancyViewEntity> findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID restaurantId, Instant endTime, Instant startTime
    );
    void deleteByReservationId(UUID reservationId);
}
