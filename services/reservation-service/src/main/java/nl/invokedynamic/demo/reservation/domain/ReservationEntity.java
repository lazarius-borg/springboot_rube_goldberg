package nl.invokedynamic.demo.reservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class ReservationEntity {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "cancellation_window_hours", nullable = false)
    private int cancellationWindowHours = 2;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ReservationEntity() {}

    public ReservationEntity(UUID id, UUID restaurantId, UUID customerId, String customerName, String customerEmail,
                             int partySize, Instant startTime, Instant endTime, String status,
                             Instant createdAt, Instant updatedAt) {
        this(id, restaurantId, customerId, customerName, customerEmail, partySize, startTime, endTime, status, 2, createdAt, updatedAt);
    }

    public ReservationEntity(UUID id, UUID restaurantId, UUID customerId, String customerName, String customerEmail,
                             int partySize, Instant startTime, Instant endTime, String status,
                             int cancellationWindowHours, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.partySize = partySize;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.cancellationWindowHours = cancellationWindowHours > 0 ? cancellationWindowHours : 2;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ReservationEntity(UUID id, UUID restaurantId, UUID customerId, String customerName, String customerEmail,
                             int partySize, Instant startTime, Instant endTime, String status,
                             int cancellationWindowHours, Instant createdAt, Instant updatedAt, String cancellationReason) {
        this(id, restaurantId, customerId, customerName, customerEmail, partySize, startTime, endTime, status, cancellationWindowHours, createdAt, updatedAt);
        this.cancellationReason = cancellationReason;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public int getPartySize() { return partySize; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setStatus(ReservationStatus status) { this.status = status.name(); }
    public int getCancellationWindowHours() { return cancellationWindowHours; }
    public void setCancellationWindowHours(int cancellationWindowHours) { this.cancellationWindowHours = cancellationWindowHours; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
