package nl.invokedynamic.demo.notification.repository;

import nl.invokedynamic.demo.notification.domain.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {}
