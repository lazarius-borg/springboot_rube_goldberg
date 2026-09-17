package nl.invokedynamic.demo.availability.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface RestaurantScheduleClient {
    boolean isClosedAt(UUID restaurantId, LocalDate date, LocalTime time);

    default OpeningHoursResponse getScheduleFor(UUID restaurantId, LocalDate date) {
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpeningHoursResponse(
            Integer dayOfWeek,
            LocalDate specificDate,
            LocalTime openTime,
            LocalTime closeTime,
            @JsonProperty("closed") @JsonAlias({"closed", "isClosed"}) boolean closed
    ) {
        public boolean isClosed() {
            return closed;
        }
    }
}
