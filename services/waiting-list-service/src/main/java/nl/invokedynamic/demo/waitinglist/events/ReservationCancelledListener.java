package nl.invokedynamic.demo.waitinglist.events;

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
            if (message.contains("ReservationCancelled")) {
                ReservationCancelledEvent event = objectMapper.readValue(message, ReservationCancelledEvent.class);
                waitingListService.processCancellationOpening(
                        event.restaurantId(), event.startTime(), event.partySize(), event.releasedTableIds()
                );
            }
        } catch (Exception ignored) {}
    }
}
