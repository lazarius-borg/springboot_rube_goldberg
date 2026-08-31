package nl.invokedynamic.demo.waitinglist.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "waiting_list_entry")
public class WaitingListEntryEntity {

    @Id
    private UUID id;
    private UUID restaurantId;
    private UUID customerId;
    private String customerEmail;
    private LocalDate targetDate;
    private LocalTime earliestTime;
    private LocalTime latestTime;
    private int partySize;
    private String status = "WAITING";
    private Instant createdAt;

    public WaitingListEntryEntity() {}

    public WaitingListEntryEntity(UUID id, UUID restaurantId, UUID customerId, String customerEmail,
                                  LocalDate targetDate, LocalTime earliestTime, LocalTime latestTime,
                                  int partySize, String status, Instant createdAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.targetDate = targetDate;
        this.earliestTime = earliestTime;
        this.latestTime = latestTime;
        this.partySize = partySize;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public LocalDate getTargetDate() { return targetDate; }
    public LocalTime getEarliestTime() { return earliestTime; }
    public LocalTime getLatestTime() { return latestTime; }
    public int getPartySize() { return partySize; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
