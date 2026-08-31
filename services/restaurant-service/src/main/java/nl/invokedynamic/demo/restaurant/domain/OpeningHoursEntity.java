package nl.invokedynamic.demo.restaurant.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "opening_hours")
public class OpeningHoursEntity {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed = false;

    public OpeningHoursEntity() {}

    public OpeningHoursEntity(UUID id, UUID restaurantId, Integer dayOfWeek, LocalDate specificDate,
                              LocalTime openTime, LocalTime closeTime, boolean closed) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.dayOfWeek = dayOfWeek;
        this.specificDate = specificDate;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = closed;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public LocalDate getSpecificDate() { return specificDate; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public boolean isClosed() { return closed; }
}
