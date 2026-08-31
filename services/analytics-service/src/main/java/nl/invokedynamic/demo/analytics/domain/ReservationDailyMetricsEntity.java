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
    }
    public long getReservationsCompletedCount() { return reservationsCompletedCount; }
    public void incrementCompleted() { this.reservationsCompletedCount++; }
    public long getReservationsCancelledCount() { return reservationsCancelledCount; }
    public void incrementCancelled() { this.reservationsCancelledCount++; }
    public long getReservationsNoShowCount() { return reservationsNoShowCount; }
    public void incrementNoShow() { this.reservationsNoShowCount++; }
    public long getTotalGuestsCount() { return totalGuestsCount; }
}
