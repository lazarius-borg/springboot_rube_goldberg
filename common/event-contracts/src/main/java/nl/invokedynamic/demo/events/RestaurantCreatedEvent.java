package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.UUID;

public record RestaurantCreatedEvent(
        UUID eventId,
        Instant timestamp,
        UUID restaurantId,
        String name,
        String timezone,
        int defaultReservationDurationMinutes,
        int minBookingAdvanceMinutes,
        int maxBookingHorizonDays,
        int cancellationWindowHours
) {}
