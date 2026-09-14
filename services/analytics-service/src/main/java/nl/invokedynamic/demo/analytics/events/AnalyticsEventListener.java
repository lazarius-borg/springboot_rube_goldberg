package nl.invokedynamic.demo.analytics.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.analytics.domain.ProcessedEventEntity;
import nl.invokedynamic.demo.analytics.repository.ProcessedEventRepository;
import nl.invokedynamic.demo.analytics.service.AnalyticsService;
import nl.invokedynamic.demo.events.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class AnalyticsEventListener {

    private final AnalyticsService analyticsService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsEventListener(AnalyticsService analyticsService,
                                  ProcessedEventRepository processedEventRepository,
                                  ObjectMapper objectMapper) {
        this.analyticsService = analyticsService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "reservation.events", groupId = "analytics-service-group")
    @Transactional
    public void onReservationEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
            if (node.has("allocatedTableIds") || message.contains("ReservationCreated")) {
                ReservationCreatedEvent event = objectMapper.treeToValue(node, ReservationCreatedEvent.class);
                if (processedEventRepository.existsById(event.eventId())) return;
                analyticsService.recordReservationCreated(
                        event.restaurantId(), event.startTime().atZone(ZoneOffset.UTC).toLocalDate(), event.partySize()
                );
                processedEventRepository.save(new ProcessedEventEntity(event.eventId(), "ReservationCreated", "analytics-group", Instant.now()));
            } else if (node.has("releasedTableIds") || node.has("reason") || message.contains("ReservationCancelled")) {
                ReservationCancelledEvent event = objectMapper.treeToValue(node, ReservationCancelledEvent.class);
                if (processedEventRepository.existsById(event.eventId())) return;
                analyticsService.recordReservationCancelled(
                        event.restaurantId(), event.startTime().atZone(ZoneOffset.UTC).toLocalDate()
                );
                processedEventRepository.save(new ProcessedEventEntity(event.eventId(), "ReservationCancelled", "analytics-group", Instant.now()));
            }
        } catch (Exception ignored) {}
    }

    @KafkaListener(topics = "waiting-list.events", groupId = "analytics-service-group")
    @Transactional
    public void onWaitingListEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
            if (node.has("reservationId") || message.contains("WaitingListOfferAccepted")) {
                WaitingListOfferAcceptedEvent event = objectMapper.treeToValue(node, WaitingListOfferAcceptedEvent.class);
                if (processedEventRepository.existsById(event.eventId())) return;
                analyticsService.recordWaitingListOfferAccepted(
                        event.restaurantId(), event.timestamp().atZone(ZoneOffset.UTC).toLocalDate()
                );
                processedEventRepository.save(new ProcessedEventEntity(event.eventId(), "WaitingListOfferAccepted", "analytics-group", Instant.now()));
            }
        } catch (Exception ignored) {}
    }
}
