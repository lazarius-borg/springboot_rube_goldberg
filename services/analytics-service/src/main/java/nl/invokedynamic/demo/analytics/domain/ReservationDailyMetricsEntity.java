package nl.invokedynamic.demo.analytics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reservation_daily_metrics")
public class ReservationDailyMetricsEntity {

    @Id
    private UUID id;
    private UUID restaurantId;
    private LocalDate metricDate;
    private long reservationsCreatedCount = 0;
    private long reservationsCompletedCount = 0;
    private long reservationsCancelledCount = 0;
    private long reservationsNoShowCount = 0;
    private long totalGuestsCount = 0;

    // Granular Party Size Buckets
    private long partySize1Count = 0;
    private long partySize2Count = 0;
    private long partySize3Count = 0;
    private long partySize4Count = 0;
    private long partySize5Count = 0;
    private long partySize6Count = 0;
    private long partySize7PlusCount = 0;

    // Cancellation Breakdown
    private long cancelledCustomerRequestCount = 0;
    private long cancelledNoShowCount = 0;
    private long cancelledRestaurantInitiatedCount = 0;

    public ReservationDailyMetricsEntity() {}

    public ReservationDailyMetricsEntity(UUID id, UUID restaurantId, LocalDate metricDate) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.metricDate = metricDate;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public LocalDate getMetricDate() { return metricDate; }
    public long getReservationsCreatedCount() { return reservationsCreatedCount; }
    public void incrementCreated(int partySize) {
        this.reservationsCreatedCount++;
        this.totalGuestsCount += partySize;
        incrementPartyBucket(partySize);
    }

    private void incrementPartyBucket(int partySize) {
        switch (partySize) {
            case 1 -> this.partySize1Count++;
            case 2 -> this.partySize2Count++;
            case 3 -> this.partySize3Count++;
            case 4 -> this.partySize4Count++;
            case 5 -> this.partySize5Count++;
            case 6 -> this.partySize6Count++;
            default -> this.partySize7PlusCount++;
        }
    }

    public long getReservationsCompletedCount() { return reservationsCompletedCount; }
    public void incrementCompleted() { this.reservationsCompletedCount++; }
    public long getReservationsCancelledCount() { return reservationsCancelledCount; }
    public void incrementCancelled() {
        incrementCancelled("CUSTOMER_REQUEST");
    }
    public void incrementCancelled(String category) {
        this.reservationsCancelledCount++;
        if ("NO_SHOW_LATE_CANCEL".equalsIgnoreCase(category)) {
            this.cancelledNoShowCount++;
        } else if ("RESTAURANT_INITIATED".equalsIgnoreCase(category)) {
            this.cancelledRestaurantInitiatedCount++;
        } else {
            this.cancelledCustomerRequestCount++;
        }
    }
    public long getReservationsNoShowCount() { return reservationsNoShowCount; }
    public void incrementNoShow() {
        this.reservationsNoShowCount++;
        this.cancelledNoShowCount++;
    }
    public long getTotalGuestsCount() { return totalGuestsCount; }

    public long getPartySize1Count() { return partySize1Count; }
    public long getPartySize2Count() { return partySize2Count; }
    public long getPartySize3Count() { return partySize3Count; }
    public long getPartySize4Count() { return partySize4Count; }
    public long getPartySize5Count() { return partySize5Count; }
    public long getPartySize6Count() { return partySize6Count; }
    public long getPartySize7PlusCount() { return partySize7PlusCount; }

    public long getCancelledCustomerRequestCount() { return cancelledCustomerRequestCount; }
    public long getCancelledNoShowCount() { return cancelledNoShowCount; }
    public long getCancelledRestaurantInitiatedCount() { return cancelledRestaurantInitiatedCount; }
}
