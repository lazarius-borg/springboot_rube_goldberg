package nl.invokedynamic.demo.waitinglist.repository;

import nl.invokedynamic.demo.waitinglist.domain.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
