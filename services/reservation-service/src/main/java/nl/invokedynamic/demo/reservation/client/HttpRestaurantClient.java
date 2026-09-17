package nl.invokedynamic.demo.reservation.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine.CombinationCandidate;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine.TableCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.*;
import java.util.*;

@Component
public class HttpRestaurantClient implements RestaurantClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRestaurantClient.class);
    private final RestClient restClient;

    public HttpRestaurantClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableResponse(UUID id, String tableNumber, int capacity, String zone, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CombinationResponse(UUID id, String name, List<UUID> tableIds, int combinedCapacity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestaurantDetailsResponse(
            UUID id, String name, String timezone,
            int minReservationDurationMinutes,
            int defaultReservationDurationMinutes,
            int maxReservationDurationMinutes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpeningHoursResponse(
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

    @Override
    public List<TableCandidate> getTableCandidates(UUID restaurantId) {
        try {
            List<TableResponse> tables = restClient.get()
                    .uri("/api/v1/restaurants/{id}/tables", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (tables == null) return List.of();
            return tables.stream()
                    .filter(t -> t.status() == null || "ACTIVE".equalsIgnoreCase(t.status()))
                    .map(t -> new TableCandidate(t.id(), t.capacity()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch tables for restaurant {}: {}", restaurantId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<UUID, String> getTableLabels(UUID restaurantId) {
        try {
            List<TableResponse> tables = restClient.get()
                    .uri("/api/v1/restaurants/{id}/tables", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (tables == null) return Map.of();
            Map<UUID, String> map = new HashMap<>();
            for (TableResponse t : tables) {
                map.put(t.id(), t.tableNumber());
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to fetch table labels for restaurant {}: {}", restaurantId, e.getMessage());
            return Map.of();
        }
    }

    @Override
    public List<CombinationCandidate> getCombinationCandidates(UUID restaurantId) {
        try {
            List<CombinationResponse> combos = restClient.get()
                    .uri("/api/v1/restaurants/{id}/table-combinations", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (combos == null) return List.of();
            return combos.stream()
                    .map(c -> new CombinationCandidate(c.id(), c.tableIds(), c.combinedCapacity()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch combinations for restaurant {}: {}", restaurantId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public int getMinReservationDurationMinutes(UUID restaurantId) {
        try {
            RestaurantDetailsResponse resp = restClient.get()
                    .uri("/api/v1/restaurants/{id}", restaurantId)
                    .retrieve()
                    .body(RestaurantDetailsResponse.class);
            if (resp != null && resp.minReservationDurationMinutes() > 0) {
                return resp.minReservationDurationMinutes();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch restaurant details for {}: {}", restaurantId, e.getMessage());
        }
        return 45;
    }

    @Override
    public int getMaxReservationDurationMinutes(UUID restaurantId) {
        try {
            RestaurantDetailsResponse resp = restClient.get()
                    .uri("/api/v1/restaurants/{id}", restaurantId)
                    .retrieve()
                    .body(RestaurantDetailsResponse.class);
            if (resp != null && resp.maxReservationDurationMinutes() > 0) {
                return resp.maxReservationDurationMinutes();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch restaurant details for {}: {}", restaurantId, e.getMessage());
        }
        return 180;
    }

    @Override
    public int getTotalCapacity(UUID restaurantId) {
        List<TableCandidate> tables = getTableCandidates(restaurantId);
        return tables.stream().mapToInt(TableCandidate::capacity).sum();
    }

    @Override
    public boolean isClosedAt(UUID restaurantId, Instant time) {
        try {
            RestaurantDetailsResponse details = restClient.get()
                    .uri("/api/v1/restaurants/{id}", restaurantId)
                    .retrieve()
                    .body(RestaurantDetailsResponse.class);
            ZoneId zoneId = ZoneId.of(details != null && details.timezone() != null ? details.timezone() : "Europe/Amsterdam");
            ZonedDateTime zdt = time.atZone(zoneId);
            LocalDate date = zdt.toLocalDate();
            int dayOfWeek = zdt.getDayOfWeek().getValue();

            List<OpeningHoursResponse> hours = restClient.get()
                    .uri("/api/v1/restaurants/{id}/opening-hours", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (hours == null || hours.isEmpty()) {
                return false;
            }

            for (OpeningHoursResponse h : hours) {
                if (date.equals(h.specificDate())) {
                    return h.isClosed();
                }
            }
            for (OpeningHoursResponse h : hours) {
                if (h.dayOfWeek() != null && h.dayOfWeek() == dayOfWeek) {
                    return h.isClosed();
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to verify closed schedule for restaurant {}: {}", restaurantId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isWithinOperatingHours(UUID restaurantId, Instant startTime, Instant endTime) {
        try {
            RestaurantDetailsResponse details = restClient.get()
                    .uri("/api/v1/restaurants/{id}", restaurantId)
                    .retrieve()
                    .body(RestaurantDetailsResponse.class);
            ZoneId zoneId = ZoneId.of(details != null && details.timezone() != null ? details.timezone() : "Europe/Amsterdam");
            ZonedDateTime startZdt = startTime.atZone(zoneId);
            ZonedDateTime endZdt = endTime.atZone(zoneId);
            LocalDate date = startZdt.toLocalDate();
            LocalTime startLocal = startZdt.toLocalTime();
            LocalTime endLocal = endZdt.toLocalTime();
            int dayOfWeek = startZdt.getDayOfWeek().getValue();

            List<OpeningHoursResponse> hours = restClient.get()
                    .uri("/api/v1/restaurants/{id}/opening-hours", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (hours == null || hours.isEmpty()) {
                return true;
            }

            OpeningHoursResponse match = null;
            for (OpeningHoursResponse h : hours) {
                if (date.equals(h.specificDate())) {
                    match = h;
                    break;
                }
            }
            if (match == null) {
                for (OpeningHoursResponse h : hours) {
                    if (h.dayOfWeek() != null && h.dayOfWeek() == dayOfWeek) {
                        match = h;
                        break;
                    }
                }
            }

            if (match == null) {
                return true;
            }

            if (match.isClosed()) {
                return false;
            }

            if (match.openTime() != null && match.closeTime() != null) {
                if (startLocal.isBefore(match.openTime())) {
                    return false;
                }
                if (endZdt.toLocalDate().isAfter(date) || endLocal.isAfter(match.closeTime())) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to verify operating window for restaurant {}: {}", restaurantId, e.getMessage());
            return true;
        }
    }
}
