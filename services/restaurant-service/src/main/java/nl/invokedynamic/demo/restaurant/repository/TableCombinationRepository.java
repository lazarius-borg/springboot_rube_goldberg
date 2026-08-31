package nl.invokedynamic.demo.restaurant.repository;

import nl.invokedynamic.demo.restaurant.domain.TableCombinationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TableCombinationRepository extends JpaRepository<TableCombinationEntity, UUID> {
    List<TableCombinationEntity> findByRestaurantId(UUID restaurantId);
}
