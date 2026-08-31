package nl.invokedynamic.demo.analytics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waiting_list_daily_metrics")
public class WaitingListDailyMetricsEntity {

    @Id
    private UUID id;
    private UUID restaurantId;
    private LocalDate metricDate;
    private long entriesCreatedCount = 0;
    private long offersCreatedCount = 0;
    private long offersAcceptedCount = 0;
    private long offersExpiredCount = 0;

    public WaitingListDailyMetricsEntity() {}

    public WaitingListDailyMetricsEntity(UUID id, UUID restaurantId, LocalDate metricDate) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.metricDate = metricDate;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public LocalDate getMetricDate() { return metricDate; }
    public long getEntriesCreatedCount() { return entriesCreatedCount; }
    public void incrementEntries() { this.entriesCreatedCount++; }
    public long getOffersCreatedCount() { return offersCreatedCount; }
    public void incrementOffersCreated() { this.offersCreatedCount++; }
    public long getOffersAcceptedCount() { return offersAcceptedCount; }
    public void incrementOffersAccepted() { this.offersAcceptedCount++; }
    public long getOffersExpiredCount() { return offersExpiredCount; }
    public void incrementOffersExpired() { this.offersExpiredCount++; }
}
