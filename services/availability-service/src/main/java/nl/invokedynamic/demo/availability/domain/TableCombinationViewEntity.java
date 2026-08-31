package nl.invokedynamic.demo.availability.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "table_combination_view")
public class TableCombinationViewEntity {

    @Id
    private UUID id;
    private UUID restaurantId;
    private String name;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "table_ids", columnDefinition = "uuid[]")
    private List<UUID> tableIds;
    private int combinedCapacity;

    public TableCombinationViewEntity() {}

    public TableCombinationViewEntity(UUID id, UUID restaurantId, String name, List<UUID> tableIds, int combinedCapacity) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.tableIds = tableIds;
        this.combinedCapacity = combinedCapacity;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public List<UUID> getTableIds() { return tableIds; }
    public int getCombinedCapacity() { return combinedCapacity; }
}
