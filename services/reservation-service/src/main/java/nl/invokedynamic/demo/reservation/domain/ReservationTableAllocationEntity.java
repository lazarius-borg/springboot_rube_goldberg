package nl.invokedynamic.demo.reservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_table_allocation")
public class ReservationTableAllocationEntity {

    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    public ReservationTableAllocationEntity() {}

    public ReservationTableAllocationEntity(UUID id, UUID reservationId, UUID tableId, UUID restaurantId, Instant startTime, Instant endTime) {
        this.id = id;
        this.reservationId = reservationId;
        this.tableId = tableId;
        this.restaurantId = restaurantId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getTableId() { return tableId; }
    public UUID getRestaurantId() { return restaurantId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
}
