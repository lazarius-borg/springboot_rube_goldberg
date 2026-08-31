package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCreatedEvent(
        UUID eventId,
        Instant timestamp,
        UUID reservationId,
        UUID restaurantId,
        UUID customerId,
        String customerName,
        String customerEmail,
        Instant startTime,
        Instant endTime,
        int partySize,
        List<UUID> allocatedTableIds
) {}
