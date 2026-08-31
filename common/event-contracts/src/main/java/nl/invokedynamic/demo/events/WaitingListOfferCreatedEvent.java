package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WaitingListOfferCreatedEvent(
        UUID eventId,
        Instant timestamp,
        UUID offerId,
        UUID waitingListEntryId,
        UUID restaurantId,
        UUID customerId,
        String customerEmail,
        Instant offeredStartTime,
        List<UUID> offeredTableIds,
        Instant expiresAt
) {}
