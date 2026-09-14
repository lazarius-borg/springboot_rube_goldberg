package nl.invokedynamic.demo.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.ReservationCancelledEvent;
import nl.invokedynamic.demo.events.ReservationCreatedEvent;
import nl.invokedynamic.demo.events.WaitingListOfferCreatedEvent;
import nl.invokedynamic.demo.notification.domain.ProcessedEventEntity;
import nl.invokedynamic.demo.notification.domain.ReminderScheduleEntity;
import nl.invokedynamic.demo.notification.mail.MailpitEmailSender;
import nl.invokedynamic.demo.notification.repository.ProcessedEventRepository;
import nl.invokedynamic.demo.notification.repository.ReminderScheduleRepository;
import nl.invokedynamic.demo.notification.template.EmailTemplateRenderer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationEventListener {

    private final MailpitEmailSender emailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final ReminderScheduleRepository reminderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(MailpitEmailSender emailSender,
                                     EmailTemplateRenderer templateRenderer,
                                     ReminderScheduleRepository reminderRepository,
                                     ProcessedEventRepository processedEventRepository,
                                     ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.templateRenderer = templateRenderer;
        this.reminderRepository = reminderRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "reservation.events", groupId = "notification-service-group")
    @Transactional
    public void onReservationEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
            if (node.has("allocatedTableIds") || message.contains("ReservationCreated")) {
                ReservationCreatedEvent event = objectMapper.treeToValue(node, ReservationCreatedEvent.class);
                if (isAlreadyProcessed(event.eventId(), "ReservationCreated")) return;

                String body = templateRenderer.renderReservationConfirmed(
                        event.customerName(), event.startTime(), event.partySize()
                );
                emailSender.sendEmail(event.customerId(), event.customerEmail(), "RESERVATION_CONFIRMED", "Reservation Confirmed", body);

                // Schedule reminder for 24h prior
                Instant reminderTime = event.startTime().minus(Duration.ofHours(24));
                reminderRepository.save(new ReminderScheduleEntity(
                        UUID.randomUUID(), event.reservationId(), event.customerId(), event.customerEmail(), reminderTime
                ));
                markProcessed(event.eventId(), "ReservationCreated");

            } else if (node.has("releasedTableIds") || node.has("reason") || message.contains("ReservationCancelled")) {
                ReservationCancelledEvent event = objectMapper.treeToValue(node, ReservationCancelledEvent.class);
                if (isAlreadyProcessed(event.eventId(), "ReservationCancelled")) return;

                String body = templateRenderer.renderReservationCancelled(event.reason());
                emailSender.sendEmail(event.customerId(), event.customerEmail(), "RESERVATION_CANCELLED", "Reservation Cancelled", body);
                reminderRepository.deleteByReservationId(event.reservationId());
                markProcessed(event.eventId(), "ReservationCancelled");
            }
        } catch (Exception ignored) {}
    }

    @KafkaListener(topics = "waiting-list.events", groupId = "notification-service-group")
    @Transactional
    public void onWaitingListEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
            if (node.has("expiresAt") || node.has("offeredTableIds") || message.contains("WaitingListOfferCreated")) {
                WaitingListOfferCreatedEvent event = objectMapper.treeToValue(node, WaitingListOfferCreatedEvent.class);
                if (isAlreadyProcessed(event.eventId(), "WaitingListOfferCreated")) return;

                String body = templateRenderer.renderWaitingListOffer(
                        event.offerId().toString(), event.offeredStartTime(), event.expiresAt()
                );
                emailSender.sendEmail(event.customerId(), event.customerEmail(), "WAITING_LIST_OFFER", "Table Available - Reservation Offer", body);
                markProcessed(event.eventId(), "WaitingListOfferCreated");
            }
        } catch (Exception ignored) {}
    }

    private boolean isAlreadyProcessed(UUID eventId, String type) {
        return processedEventRepository.existsById(eventId);
    }

    private void markProcessed(UUID eventId, String type) {
        processedEventRepository.save(new ProcessedEventEntity(eventId, type, "notification-service-group", Instant.now()));
    }
}
