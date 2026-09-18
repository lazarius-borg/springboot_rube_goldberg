package nl.invokedynamic.demo.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.availability.client.RestaurantScheduleClient;
import nl.invokedynamic.demo.availability.domain.*;
import nl.invokedynamic.demo.availability.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceUnitTest {

    @Mock private RestaurantViewRepository restaurantRepository;
    @Mock private TableInventoryViewRepository tableRepository;
    @Mock private TableCombinationViewRepository combinationRepository;
    @Mock private SlotOccupancyViewRepository occupancyRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AvailabilityService(
                restaurantRepository, tableRepository, combinationRepository,
                occupancyRepository, redisTemplate, objectMapper,
                new RestaurantTimezoneResolver(), null
        );
    }

    @Test
    void shouldReturnAvailableWhenFreeSingleTableExists() {
        UUID restId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity table = new TableInventoryViewEntity(UUID.randomUUID(), restId, "T1", 4);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of(table));
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(occupancyRepository.findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(restId), any(), any()))
                .thenReturn(List.of());

        LocalDate targetDate = LocalDate.now().plusDays(1);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restId, targetDate, LocalTime.of(19, 0), 4
        );

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.availableSlots()).isNotEmpty();
    }

    @Test
    void shouldReturnUnavailableWhenAllTablesOccupied() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity table = new TableInventoryViewEntity(tableId, restId, "T1", 4);
        SlotOccupancyViewEntity occ = new SlotOccupancyViewEntity(
                UUID.randomUUID(), UUID.randomUUID(), restId, tableId, Instant.now(), Instant.now().plusSeconds(5400)
        );

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of(table));
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(occupancyRepository.findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(restId), any(), any()))
                .thenReturn(List.of(occ));

        LocalDate targetDate = LocalDate.now().plusDays(1);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restId, targetDate, LocalTime.of(19, 0), 4
        );

        assertThat(result.isAvailable()).isFalse();
    }

    @Test
    void shouldThrowWhenRequestedTimeIsInThePast() {
        UUID restId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));

        LocalDate pastDate = LocalDate.now().minusDays(1);
        assertThatThrownBy(() -> service.checkAvailability(restId, pastDate, LocalTime.of(12, 0), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dining time cannot be in the past");
    }

    @Test
    void shouldThrowWhenRequestedTimeIsWithinMinimumAdvanceWindow() {
        UUID restId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Bistro", "Europe/Amsterdam", 90, 60, 60, 2, Instant.now()
        );
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));

        ZoneId zone = ZoneId.of("Europe/Amsterdam");
        ZonedDateTime now = ZonedDateTime.now(zone);
        // Request 10 minutes in the future when 60 minutes advance is required
        ZonedDateTime requested = now.plusMinutes(10);

        assertThatThrownBy(() -> service.checkAvailability(restId, requested.toLocalDate(), requested.toLocalTime(), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dining time must be at least 60 minutes in advance");
    }

    @Test
    void shouldThrowWhenRequestedTimeIsBeforeOpeningHour() {
        UUID restId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Bistro", "Europe/Amsterdam", 45, 90, 180, 0, 60, 2, Instant.now()
        );
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));

        RestaurantScheduleClient mockSchedule = mock(RestaurantScheduleClient.class);
        when(mockSchedule.getScheduleFor(eq(restId), any(LocalDate.class)))
                .thenReturn(new RestaurantScheduleClient.OpeningHoursResponse(
                        null, null, LocalTime.of(17, 0), LocalTime.of(23, 0), false
                ));

        AvailabilityService testService = new AvailabilityService(
                restaurantRepository, tableRepository, combinationRepository,
                occupancyRepository, redisTemplate, objectMapper,
                new RestaurantTimezoneResolver(), mockSchedule
        );

        LocalDate futureDate = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> testService.checkAvailability(restId, futureDate, LocalTime.of(15, 0), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is before opening time of 17:00");
    }

    @Test
    void shouldThrowWhenRequestedTimeIsTooCloseToClosingHour() {
        UUID restId = UUID.randomUUID();
        // minReservationDuration = 45 minutes
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Bistro", "Europe/Amsterdam", 45, 90, 180, 0, 60, 2, Instant.now()
        );
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));

        RestaurantScheduleClient mockSchedule = mock(RestaurantScheduleClient.class);
        when(mockSchedule.getScheduleFor(eq(restId), any(LocalDate.class)))
                .thenReturn(new RestaurantScheduleClient.OpeningHoursResponse(
                        null, null, LocalTime.of(17, 0), LocalTime.of(23, 0), false
                ));

        AvailabilityService testService = new AvailabilityService(
                restaurantRepository, tableRepository, combinationRepository,
                occupancyRepository, redisTemplate, objectMapper,
                new RestaurantTimezoneResolver(), mockSchedule
        );

        LocalDate futureDate = LocalDate.now().plusDays(2);
        // Closing is 23:00, minDuration is 45 min -> latest seating is 22:15. Request 22:30.
        assertThatThrownBy(() -> testService.checkAvailability(restId, futureDate, LocalTime.of(22, 30), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is too close to closing time (23:00); latest seating allowed is 22:15");
    }
}
