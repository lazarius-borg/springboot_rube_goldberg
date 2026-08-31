package nl.invokedynamic.demo.notification.repository;

import nl.invokedynamic.demo.notification.domain.ReminderScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReminderScheduleRepository extends JpaRepository<ReminderScheduleEntity, UUID> {
    List<ReminderScheduleEntity> findByStatusAndScheduledReminderTimeBefore(String status, Instant now);
    void deleteByReservationId(UUID reservationId);
}
