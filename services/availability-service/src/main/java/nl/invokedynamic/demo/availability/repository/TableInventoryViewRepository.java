package nl.invokedynamic.demo.availability.repository;

import nl.invokedynamic.demo.availability.domain.TableInventoryViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TableInventoryViewRepository extends JpaRepository<TableInventoryViewEntity, UUID> {
    List<TableInventoryViewEntity> findByRestaurantId(UUID restaurantId);
}
