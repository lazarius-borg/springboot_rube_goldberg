package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WaitingListEntryCreatedEvent(
        UUID eventId,
        Instant timestamp,
        UUID entryId,
        UUID restaurantId,
        UUID customerId,
        String customerEmail,
        LocalDate targetDate,
        LocalTime earliestTime,
        LocalTime latestTime,
        int partySize
) {}
