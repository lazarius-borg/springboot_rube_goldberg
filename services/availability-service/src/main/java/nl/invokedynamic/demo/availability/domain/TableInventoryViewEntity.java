package nl.invokedynamic.demo.availability.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "table_inventory_view")
public class TableInventoryViewEntity {

    @Id
    private UUID id;
    private UUID restaurantId;
    private String tableNumber;
    private int capacity;

    public TableInventoryViewEntity() {}

    public TableInventoryViewEntity(UUID id, UUID restaurantId, String tableNumber, int capacity) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getTableNumber() { return tableNumber; }
    public int getCapacity() { return capacity; }
}
