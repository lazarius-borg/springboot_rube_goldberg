package nl.invokedynamic.demo.availability.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Component
public class HttpRestaurantScheduleClient implements RestaurantScheduleClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRestaurantScheduleClient.class);
    private final RestClient restClient;

    public HttpRestaurantScheduleClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OpeningHoursResponse getScheduleFor(UUID restaurantId, LocalDate date) {
        try {
            List<OpeningHoursResponse> hours = restClient.get()
                    .uri("/api/v1/restaurants/{id}/opening-hours", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (hours == null || hours.isEmpty()) {
                return null;
            }

            int dow = date.getDayOfWeek().getValue();
            // Specific date takes precedence
            for (OpeningHoursResponse h : hours) {
                if (date.equals(h.specificDate())) {
                    return h;
                }
            }

            // Fallback to dayOfWeek
            for (OpeningHoursResponse h : hours) {
                if (h.dayOfWeek() != null && h.dayOfWeek() == dow) {
                    return h;
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to retrieve schedule for restaurant {}: {}", restaurantId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isClosedAt(UUID restaurantId, LocalDate date, LocalTime time) {
        OpeningHoursResponse h = getScheduleFor(restaurantId, date);
        if (h == null) return false;
        if (h.isClosed()) return true;
        if (h.openTime() != null && h.closeTime() != null) {
            return time.isBefore(h.openTime()) || !time.isBefore(h.closeTime());
        }
        return false;
    }
}
