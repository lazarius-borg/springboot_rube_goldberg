package nl.invokedynamic.demo.waitinglist.repository;

import nl.invokedynamic.demo.waitinglist.domain.WaitingListEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WaitingListEntryRepository extends JpaRepository<WaitingListEntryEntity, UUID> {
    List<WaitingListEntryEntity> findByRestaurantIdAndTargetDateAndStatusOrderByCreatedAtAsc(UUID restaurantId, LocalDate targetDate, String status);
    List<WaitingListEntryEntity> findByRestaurantIdAndTargetDateOrderByCreatedAtAsc(UUID restaurantId, LocalDate targetDate);
    List<WaitingListEntryEntity> findByRestaurantIdAndStatusOrderByCreatedAtAsc(UUID restaurantId, String status);
    List<WaitingListEntryEntity> findByRestaurantIdOrderByCreatedAtAsc(UUID restaurantId);
    List<WaitingListEntryEntity> findByCustomerId(UUID customerId);
}
