package nl.invokedynamic.demo.availability.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_occupancy_view")
public class SlotOccupancyViewEntity {

    @Id
    private UUID id;
    private UUID reservationId;
    private UUID restaurantId;
    private UUID tableId;
    private Instant startTime;
    private Instant endTime;

    public SlotOccupancyViewEntity() {}

    public SlotOccupancyViewEntity(UUID id, UUID reservationId, UUID restaurantId, UUID tableId, Instant startTime, Instant endTime) {
        this.id = id;
        this.reservationId = reservationId;
        this.restaurantId = restaurantId;
        this.tableId = tableId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getTableId() { return tableId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
}
