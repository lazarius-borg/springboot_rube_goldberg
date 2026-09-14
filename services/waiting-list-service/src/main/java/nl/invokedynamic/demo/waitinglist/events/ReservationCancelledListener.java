package nl.invokedynamic.demo.waitinglist.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.ReservationCancelledEvent;
import nl.invokedynamic.demo.waitinglist.service.WaitingListService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReservationCancelledListener {

    private final WaitingListService waitingListService;
    private final ObjectMapper objectMapper;

    public ReservationCancelledListener(WaitingListService waitingListService, ObjectMapper objectMapper) {
        this.waitingListService = waitingListService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "reservation.events", groupId = "waiting-list-service-group")
    public void onReservationEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            JsonNode node = objectMapper.readTree(message);
            if (node.has("releasedTableIds") || node.has("reason") || message.contains("ReservationCancelled")) {
                ReservationCancelledEvent event = objectMapper.treeToValue(node, ReservationCancelledEvent.class);
                waitingListService.processCancellationOpening(
                        event.restaurantId(), event.startTime(), event.partySize(), event.releasedTableIds()
                );
            }
        } catch (Exception ignored) {}
    }
}
