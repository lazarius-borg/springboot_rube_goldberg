package nl.invokedynamic.demo.availability.service;

import nl.invokedynamic.demo.availability.domain.RestaurantViewEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class RestaurantTimezoneResolver {

    private static final Logger log = LoggerFactory.getLogger(RestaurantTimezoneResolver.class);
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Amsterdam");

    public ZoneId resolveZoneId(RestaurantViewEntity restaurant) {
        if (restaurant == null || restaurant.getTimezone() == null || restaurant.getTimezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(restaurant.getTimezone());
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for restaurant {}, falling back to {}",
                    restaurant.getTimezone(), restaurant.getId(), DEFAULT_ZONE);
            return DEFAULT_ZONE;
        }
    }
}
