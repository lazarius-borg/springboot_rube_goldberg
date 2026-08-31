package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCancelledEvent(
        UUID eventId,
        Instant timestamp,
        UUID reservationId,
        UUID restaurantId,
        UUID customerId,
        String customerEmail,
        List<UUID> releasedTableIds,
        Instant startTime,
        Instant endTime,
        int partySize,
        String reason
) {}
