package nl.invokedynamic.demo.analytics.repository;

import nl.invokedynamic.demo.analytics.domain.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {}
