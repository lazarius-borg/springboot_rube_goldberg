package nl.invokedynamic.demo.events;

import java.time.Instant;
import java.util.UUID;

public record NotificationRequestedEvent(
        UUID eventId,
        Instant timestamp,
        UUID customerId,
        String recipientEmail,
        String notificationType,
        String subject,
        String body,
        String correlationId
) {}
