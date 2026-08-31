package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.UUID;

public record WaitingListOfferExpiredEvent(
        UUID eventId,
        Instant timestamp,
        UUID offerId,
        UUID waitingListEntryId,
        UUID restaurantId
) {}
