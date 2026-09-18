package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.UUID;

public record RestaurantCreatedEvent(
        UUID eventId,
        Instant timestamp,
        UUID restaurantId,
        String name,
        String timezone,
        int minReservationDurationMinutes,
        int defaultReservationDurationMinutes,
        int maxReservationDurationMinutes,
        int minBookingAdvanceMinutes,
        int maxBookingHorizonDays,
        int cancellationWindowHours
) {
    public RestaurantCreatedEvent(
            UUID eventId,
            Instant timestamp,
            UUID restaurantId,
            String name,
            String timezone,
            int defaultReservationDurationMinutes,
            int minBookingAdvanceMinutes,
            int maxBookingHorizonDays,
            int cancellationWindowHours
    ) {
        this(eventId, timestamp, restaurantId, name, timezone, 45, defaultReservationDurationMinutes, 180,
                minBookingAdvanceMinutes, maxBookingHorizonDays, cancellationWindowHours);
    }
}
