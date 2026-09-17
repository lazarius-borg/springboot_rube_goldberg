package nl.invokedynamic.demo.restaurant.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class HttpReservationClient implements ReservationClient {

    private static final Logger log = LoggerFactory.getLogger(HttpReservationClient.class);

    private final RestClient restClient;

    public HttpReservationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean hasActiveUpcomingReservations(UUID restaurantId, UUID tableId) {
        try {
            Boolean result = restClient.get()
                    .uri("/api/v1/reservations/tables/{tableId}/has-active", tableId)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to check active reservations for table {} from reservation-service: {}", tableId, e.getMessage());
            return false;
        }
    }
}
