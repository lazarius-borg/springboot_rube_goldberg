package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.UUID;

public record WaitingListOfferAcceptedEvent(
        UUID eventId,
        Instant timestamp,
        UUID offerId,
        UUID waitingListEntryId,
        UUID reservationId,
        UUID restaurantId,
        UUID customerId
) {}
