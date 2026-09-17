package nl.invokedynamic.demo.restaurant.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_table")
public class RestaurantTableEntity {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "table_number", nullable = false, length = 50)
    private String tableNumber;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 50)
    private String zone = "Main Dining";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RestaurantTableEntity() {}

    public RestaurantTableEntity(UUID id, UUID restaurantId, String tableNumber, int capacity, String status, Instant createdAt) {
        this(id, restaurantId, tableNumber, capacity, "Main Dining", status, createdAt);
    }

    public RestaurantTableEntity(UUID id, UUID restaurantId, String tableNumber, int capacity, String zone) {
        this(id, restaurantId, tableNumber, capacity, zone, "ACTIVE", Instant.now());
    }

    public RestaurantTableEntity(UUID id, UUID restaurantId, String tableNumber, int capacity, String zone, String status, Instant createdAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.zone = (zone != null && !zone.isBlank()) ? zone : "Main Dining";
        this.status = (status != null && !status.isBlank()) ? status : "ACTIVE";
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = (zone != null && !zone.isBlank()) ? zone : "Main Dining"; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
