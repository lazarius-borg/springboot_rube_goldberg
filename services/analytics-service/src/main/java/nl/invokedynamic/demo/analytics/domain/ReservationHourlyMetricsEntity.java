package nl.invokedynamic.demo.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reservation_hourly_metrics")
public class ReservationHourlyMetricsEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private LocalDate metricDate;

    @Column(nullable = false)
    private int hourOfDay;

    @Column(nullable = false)
    private long reservationCount = 0;

    public ReservationHourlyMetricsEntity() {}

    public ReservationHourlyMetricsEntity(UUID id, UUID restaurantId, LocalDate metricDate, int hourOfDay) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.metricDate = metricDate;
        this.hourOfDay = hourOfDay;
        this.reservationCount = 0;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public LocalDate getMetricDate() { return metricDate; }
    public int getHourOfDay() { return hourOfDay; }
    public long getReservationCount() { return reservationCount; }

    public void incrementCount() {
        this.reservationCount++;
    }
}
