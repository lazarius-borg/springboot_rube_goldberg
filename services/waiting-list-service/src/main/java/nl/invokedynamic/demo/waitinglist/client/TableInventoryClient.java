package nl.invokedynamic.demo.waitinglist.client;

import java.util.List;
import java.util.UUID;

public interface TableInventoryClient {
    int getReleasedCapacity(UUID restaurantId, List<UUID> releasedTableIds);
}
