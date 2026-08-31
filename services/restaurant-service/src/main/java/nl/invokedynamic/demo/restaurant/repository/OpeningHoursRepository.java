package nl.invokedynamic.demo.restaurant.repository;

import nl.invokedynamic.demo.restaurant.domain.OpeningHoursEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OpeningHoursRepository extends JpaRepository<OpeningHoursEntity, UUID> {
    List<OpeningHoursEntity> findByRestaurantId(UUID restaurantId);
}
