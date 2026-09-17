package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TableConfigurationChangedEvent(
        UUID eventId,
        Instant timestamp,
        UUID restaurantId,
        List<TableConfig> tables,
        List<CombinationConfig> combinations
) {
    public record TableConfig(UUID tableId, String tableNumber, int capacity, String zone) {
        public TableConfig(UUID tableId, String tableNumber, int capacity) {
            this(tableId, tableNumber, capacity, "Main Dining");
        }
    }
    public record CombinationConfig(UUID combinationId, String name, List<UUID> tableIds, int combinedCapacity) {}
}

