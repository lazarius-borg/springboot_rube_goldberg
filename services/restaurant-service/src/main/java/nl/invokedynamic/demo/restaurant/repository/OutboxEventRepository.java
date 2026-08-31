package nl.invokedynamic.demo.restaurant.repository;

import nl.invokedynamic.demo.restaurant.domain.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
