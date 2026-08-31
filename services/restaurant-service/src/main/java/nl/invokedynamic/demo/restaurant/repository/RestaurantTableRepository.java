package nl.invokedynamic.demo.restaurant.repository;

import nl.invokedynamic.demo.restaurant.domain.RestaurantTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTableEntity, UUID> {
    List<RestaurantTableEntity> findByRestaurantId(UUID restaurantId);
}
