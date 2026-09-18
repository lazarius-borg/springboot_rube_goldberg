package nl.invokedynamic.demo.restaurant.api.dto;

import java.util.List;
import java.util.UUID;

public record TableCombinationResponse(
        UUID id,
        UUID restaurantId,
        String name,
        String zone,
        List<UUID> tableIds,
        List<String> tableNumbers,
        int combinedCapacity
) {}
