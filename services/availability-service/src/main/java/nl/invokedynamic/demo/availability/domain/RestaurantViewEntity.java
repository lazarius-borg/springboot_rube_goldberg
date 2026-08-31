package nl.invokedynamic.demo.availability.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_view")
public class RestaurantViewEntity {

    @Id
    private UUID id;
    private String name;
    private String timezone;
    private int defaultReservationDurationMinutes;
    private int minBookingAdvanceMinutes;
    private int maxBookingHorizonDays;
    private int cancellationWindowHours;
    private Instant updatedAt;

    public RestaurantViewEntity() {}

    public RestaurantViewEntity(UUID id, String name, String timezone,
                                int defaultReservationDurationMinutes, int minBookingAdvanceMinutes,
                                int maxBookingHorizonDays, int cancellationWindowHours, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
        this.defaultReservationDurationMinutes = defaultReservationDurationMinutes;
        this.minBookingAdvanceMinutes = minBookingAdvanceMinutes;
        this.maxBookingHorizonDays = maxBookingHorizonDays;
        this.cancellationWindowHours = cancellationWindowHours;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
    public int getDefaultReservationDurationMinutes() { return defaultReservationDurationMinutes; }
    public int getMinBookingAdvanceMinutes() { return minBookingAdvanceMinutes; }
    public int getMaxBookingHorizonDays() { return maxBookingHorizonDays; }
    public int getCancellationWindowHours() { return cancellationWindowHours; }
    public Instant getUpdatedAt() { return updatedAt; }
}
