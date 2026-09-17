package nl.invokedynamic.demo.reservation.client;

import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine.CombinationCandidate;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine.TableCandidate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RestaurantClient {
    List<TableCandidate> getTableCandidates(UUID restaurantId);
    Map<UUID, String> getTableLabels(UUID restaurantId);
    List<CombinationCandidate> getCombinationCandidates(UUID restaurantId);
    int getMinReservationDurationMinutes(UUID restaurantId);
    int getMaxReservationDurationMinutes(UUID restaurantId);
    int getTotalCapacity(UUID restaurantId);
    boolean isClosedAt(UUID restaurantId, Instant time);
    default boolean isWithinOperatingHours(UUID restaurantId, Instant startTime, Instant endTime) {
        return true;
    }
}
