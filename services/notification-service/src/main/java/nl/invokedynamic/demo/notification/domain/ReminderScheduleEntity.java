package nl.invokedynamic.demo.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminder_schedule")
public class ReminderScheduleEntity {

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private UUID reservationId;
    private UUID customerId;
    private String customerEmail;
    private Instant scheduledReminderTime;
    private String status = "SCHEDULED";

    public ReminderScheduleEntity() {}

    public ReminderScheduleEntity(UUID id, UUID reservationId, UUID customerId, String customerEmail, Instant scheduledReminderTime) {
        this.id = id;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.scheduledReminderTime = scheduledReminderTime;
        this.status = "SCHEDULED";
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public Instant getScheduledReminderTime() { return scheduledReminderTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
