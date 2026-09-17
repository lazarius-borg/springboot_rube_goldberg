package nl.invokedynamic.demo.restaurant.client;

import java.util.UUID;

public interface ReservationClient {
    boolean hasActiveUpcomingReservations(UUID restaurantId, UUID tableId);
}
