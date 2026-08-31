package nl.invokedynamic.demo.availability.repository;

import nl.invokedynamic.demo.availability.domain.TableCombinationViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TableCombinationViewRepository extends JpaRepository<TableCombinationViewEntity, UUID> {
    List<TableCombinationViewEntity> findByRestaurantId(UUID restaurantId);
}
