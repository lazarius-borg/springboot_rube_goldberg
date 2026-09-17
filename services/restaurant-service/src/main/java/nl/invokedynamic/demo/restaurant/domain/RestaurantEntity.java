package nl.invokedynamic.demo.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant")
public class RestaurantEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "min_reservation_duration_minutes", nullable = false)
    private int minReservationDurationMinutes = 45;

    @Column(name = "default_reservation_duration_minutes", nullable = false)
    private int defaultReservationDurationMinutes = 90;

    @Column(name = "max_reservation_duration_minutes", nullable = false)
    private int maxReservationDurationMinutes = 180;

    @Column(name = "min_booking_advance_minutes", nullable = false)
    private int minBookingAdvanceMinutes = 30;

    @Column(name = "max_booking_horizon_days", nullable = false)
    private int maxBookingHorizonDays = 60;

    @Column(name = "cancellation_window_hours", nullable = false)
    private int cancellationWindowHours = 2;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RestaurantEntity() {}

    public RestaurantEntity(UUID id, String name, String address, String timezone,
                            int defaultReservationDurationMinutes, int minBookingAdvanceMinutes,
                            int maxBookingHorizonDays, int cancellationWindowHours,
                            String status, Instant createdAt, Instant updatedAt) {
        this(id, name, address, timezone, 45, defaultReservationDurationMinutes, 180, minBookingAdvanceMinutes,
             maxBookingHorizonDays, cancellationWindowHours, status, createdAt, updatedAt);
    }

    public RestaurantEntity(UUID id, String name, String address, String timezone,
                            int defaultReservationDurationMinutes, int maxReservationDurationMinutes,
                            int minBookingAdvanceMinutes, int maxBookingHorizonDays,
                            int cancellationWindowHours, String status, Instant createdAt, Instant updatedAt) {
        this(id, name, address, timezone, 45, defaultReservationDurationMinutes, maxReservationDurationMinutes,
             minBookingAdvanceMinutes, maxBookingHorizonDays, cancellationWindowHours, status, createdAt, updatedAt);
    }

    public RestaurantEntity(UUID id, String name, String address, String timezone,
                            int minReservationDurationMinutes, int defaultReservationDurationMinutes,
                            int maxReservationDurationMinutes, int minBookingAdvanceMinutes,
                            int maxBookingHorizonDays, int cancellationWindowHours,
                            String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.timezone = timezone;
        this.minReservationDurationMinutes = minReservationDurationMinutes > 0 ? minReservationDurationMinutes : 45;
        this.defaultReservationDurationMinutes = defaultReservationDurationMinutes;
        this.maxReservationDurationMinutes = maxReservationDurationMinutes > 0 ? maxReservationDurationMinutes : 180;
        this.minBookingAdvanceMinutes = minBookingAdvanceMinutes;
        this.maxBookingHorizonDays = maxBookingHorizonDays;
        this.cancellationWindowHours = cancellationWindowHours;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public int getMinReservationDurationMinutes() { return minReservationDurationMinutes; }
    public void setMinReservationDurationMinutes(int minDuration) { this.minReservationDurationMinutes = minDuration; }
    public int getDefaultReservationDurationMinutes() { return defaultReservationDurationMinutes; }
    public void setDefaultReservationDurationMinutes(int duration) { this.defaultReservationDurationMinutes = duration; }
    public int getMaxReservationDurationMinutes() { return maxReservationDurationMinutes; }
    public void setMaxReservationDurationMinutes(int maxDuration) { this.maxReservationDurationMinutes = maxDuration; }
    public int getMinBookingAdvanceMinutes() { return minBookingAdvanceMinutes; }
    public void setMinBookingAdvanceMinutes(int minBookingAdvanceMinutes) { this.minBookingAdvanceMinutes = minBookingAdvanceMinutes; }
    public int getMaxBookingHorizonDays() { return maxBookingHorizonDays; }
    public void setMaxBookingHorizonDays(int maxBookingHorizonDays) { this.maxBookingHorizonDays = maxBookingHorizonDays; }
    public int getCancellationWindowHours() { return cancellationWindowHours; }
    public void setCancellationWindowHours(int cancellationWindowHours) { this.cancellationWindowHours = cancellationWindowHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
