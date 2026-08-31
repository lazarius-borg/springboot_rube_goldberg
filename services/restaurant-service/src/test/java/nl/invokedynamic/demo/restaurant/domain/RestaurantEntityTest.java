package nl.invokedynamic.demo.restaurant.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantEntityTest {

    @Test
    void shouldCreateRestaurantWithDefaults() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        RestaurantEntity entity = new RestaurantEntity(
                id, "Bistro Goldberg", "123 Main St", "Europe/Amsterdam",
                90, 30, 60, 2, "ACTIVE", now, now
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getName()).isEqualTo("Bistro Goldberg");
        assertThat(entity.getTimezone()).isEqualTo("Europe/Amsterdam");
        assertThat(entity.getDefaultReservationDurationMinutes()).isEqualTo(90);
        assertThat(entity.getMinBookingAdvanceMinutes()).isEqualTo(30);
        assertThat(entity.getMaxBookingHorizonDays()).isEqualTo(60);
        assertThat(entity.getCancellationWindowHours()).isEqualTo(2);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldCreateOpeningHoursWithDayOfWeek() {
        UUID restId = UUID.randomUUID();
        OpeningHoursEntity oh = new OpeningHoursEntity(
                UUID.randomUUID(), restId, 1, null, LocalTime.of(12, 0), LocalTime.of(22, 0), false
        );

        assertThat(oh.getDayOfWeek()).isEqualTo(1);
        assertThat(oh.getSpecificDate()).isNull();
        assertThat(oh.getOpenTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(oh.getCloseTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(oh.isClosed()).isFalse();
    }

    @Test
    void shouldCreateHolidayClosure() {
        UUID restId = UUID.randomUUID();
        LocalDate christmas = LocalDate.of(2026, 12, 25);
        OpeningHoursEntity holiday = new OpeningHoursEntity(
                UUID.randomUUID(), restId, null, christmas, LocalTime.MIN, LocalTime.MAX, true
        );

        assertThat(holiday.getSpecificDate()).isEqualTo(christmas);
        assertThat(holiday.isClosed()).isTrue();
    }
}
