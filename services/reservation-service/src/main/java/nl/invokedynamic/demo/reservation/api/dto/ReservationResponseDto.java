package nl.invokedynamic.demo.reservation.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponseDto(
        UUID id,
        UUID restaurantId,
        UUID customerId,
        String customerName,
        String customerEmail,
        int partySize,
        Instant startTime,
        Instant endTime,
        String status,
        List<UUID> allocatedTableIds,
        List<String> allocatedTableLabels,
        String cancellationReason
) {
    public ReservationResponseDto(UUID id, UUID restaurantId, UUID customerId, String customerName,
                                  String customerEmail, int partySize, Instant startTime, Instant endTime,
                                  String status, List<UUID> allocatedTableIds) {
        this(id, restaurantId, customerId, customerName, customerEmail, partySize, startTime, endTime, status, allocatedTableIds, List.of(), null);
    }

    public List<UUID> allocatedTables() {
        return allocatedTableIds;
    }
}
