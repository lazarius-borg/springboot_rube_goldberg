package nl.invokedynamic.demo.availability.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_view")
public class RestaurantViewEntity {

    @Id
    private UUID id;
    private String name;
    private String timezone;
    private int minReservationDurationMinutes = 45;
    private int defaultReservationDurationMinutes;
    private int maxReservationDurationMinutes = 180;
    private int minBookingAdvanceMinutes;
    private int maxBookingHorizonDays;
    private int cancellationWindowHours;
    private Instant updatedAt;

    public RestaurantViewEntity() {}

    public RestaurantViewEntity(UUID id, String name, String timezone,
                                int defaultReservationDurationMinutes, int minBookingAdvanceMinutes,
                                int maxBookingHorizonDays, int cancellationWindowHours, Instant updatedAt) {
        this(id, name, timezone, 45, defaultReservationDurationMinutes, 180, minBookingAdvanceMinutes, maxBookingHorizonDays, cancellationWindowHours, updatedAt);
    }

    public RestaurantViewEntity(UUID id, String name, String timezone,
                                int defaultReservationDurationMinutes, int maxReservationDurationMinutes,
                                int minBookingAdvanceMinutes, int maxBookingHorizonDays,
                                int cancellationWindowHours, Instant updatedAt) {
        this(id, name, timezone, 45, defaultReservationDurationMinutes, maxReservationDurationMinutes,
                minBookingAdvanceMinutes, maxBookingHorizonDays, cancellationWindowHours, updatedAt);
    }

    public RestaurantViewEntity(UUID id, String name, String timezone,
                                int minReservationDurationMinutes, int defaultReservationDurationMinutes,
                                int maxReservationDurationMinutes, int minBookingAdvanceMinutes,
                                int maxBookingHorizonDays, int cancellationWindowHours, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
        this.minReservationDurationMinutes = minReservationDurationMinutes > 0 ? minReservationDurationMinutes : 45;
        this.defaultReservationDurationMinutes = defaultReservationDurationMinutes;
        this.maxReservationDurationMinutes = maxReservationDurationMinutes > 0 ? maxReservationDurationMinutes : 180;
        this.minBookingAdvanceMinutes = minBookingAdvanceMinutes;
        this.maxBookingHorizonDays = maxBookingHorizonDays;
        this.cancellationWindowHours = cancellationWindowHours;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
    public int getMinReservationDurationMinutes() { return minReservationDurationMinutes; }
    public int getDefaultReservationDurationMinutes() { return defaultReservationDurationMinutes; }
    public int getMaxReservationDurationMinutes() { return maxReservationDurationMinutes; }
    public int getMinBookingAdvanceMinutes() { return minBookingAdvanceMinutes; }
    public int getMaxBookingHorizonDays() { return maxBookingHorizonDays; }
    public int getCancellationWindowHours() { return cancellationWindowHours; }
    public Instant getUpdatedAt() { return updatedAt; }
}
