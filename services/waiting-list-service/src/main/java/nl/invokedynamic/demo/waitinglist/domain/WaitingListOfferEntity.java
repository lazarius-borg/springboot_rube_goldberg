package nl.invokedynamic.demo.waitinglist.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "waiting_list_offer")
public class WaitingListOfferEntity {

    @Id
    private UUID id;
    private UUID waitingListEntryId;
    private UUID restaurantId;
    private Instant offeredStartTime;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "offered_table_ids", columnDefinition = "uuid[]")
    private List<UUID> offeredTableIds;
    private Instant expiresAt;
    private String status = "PENDING";
    private Instant createdAt;
    private Instant updatedAt;

    public WaitingListOfferEntity() {}

    public WaitingListOfferEntity(UUID id, UUID waitingListEntryId, UUID restaurantId, Instant offeredStartTime,
                                  List<UUID> offeredTableIds, Instant expiresAt, String status,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.waitingListEntryId = waitingListEntryId;
        this.restaurantId = restaurantId;
        this.offeredStartTime = offeredStartTime;
        this.offeredTableIds = offeredTableIds;
        this.expiresAt = expiresAt;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getWaitingListEntryId() { return waitingListEntryId; }
    public UUID getRestaurantId() { return restaurantId; }
    public Instant getOfferedStartTime() { return offeredStartTime; }
    public List<UUID> getOfferedTableIds() { return offeredTableIds; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
