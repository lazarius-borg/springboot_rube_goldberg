package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.UUID;

public record ReservationStatusChangedEvent(
        UUID eventId,
        Instant timestamp,
        UUID reservationId,
        UUID restaurantId,
        String previousStatus,
        String newStatus
) {}
