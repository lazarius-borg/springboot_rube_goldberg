package nl.invokedynamic.demo.restaurant.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "table_combination")
public class TableCombinationEntity {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 50)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "table_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> tableIds;

    @Column(name = "combined_capacity", nullable = false)
    private int combinedCapacity;

    public TableCombinationEntity() {}

    public TableCombinationEntity(UUID id, UUID restaurantId, String name, List<UUID> tableIds, int combinedCapacity) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.tableIds = tableIds;
        this.combinedCapacity = combinedCapacity;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<UUID> getTableIds() { return tableIds; }
    public int getCombinedCapacity() { return combinedCapacity; }
    public void setCombinedCapacity(int combinedCapacity) { this.combinedCapacity = combinedCapacity; }
}

