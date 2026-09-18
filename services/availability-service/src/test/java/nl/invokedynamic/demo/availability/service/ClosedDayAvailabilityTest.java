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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClosedDayAvailabilityTest {

    @Mock private RestaurantViewRepository restaurantRepository;
    @Mock private TableInventoryViewRepository tableRepository;
    @Mock private TableCombinationViewRepository combinationRepository;
    @Mock private SlotOccupancyViewRepository occupancyRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RestaurantScheduleClient scheduleClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AvailabilityService(
                restaurantRepository, tableRepository, combinationRepository,
                occupancyRepository, redisTemplate, objectMapper,
                new RestaurantTimezoneResolver(), scheduleClient
        );
    }

    @Test
    void shouldReturnClosedWhenScheduleDesignatesClosedDay() {
        UUID restId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Closed Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity table = new TableInventoryViewEntity(UUID.randomUUID(), restId, "T1", 4);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of(table));
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(scheduleClient.isClosedAt(eq(restId), any(LocalDate.class), any(LocalTime.class))).thenReturn(true);

        LocalDate targetDate = LocalDate.now().plusDays(7);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restId, targetDate, LocalTime.of(19, 0), 2
        );

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.isClosed()).isTrue();
        assertThat(result.reason()).isEqualTo("RESTAURANT_CLOSED");
        assertThat(result.availableSlots()).isEmpty();
    }

    @Test
    void shouldReturnExceedsTotalCapacityWhenPartySizeExceedsRestaurantCapacity() {
        UUID restId = UUID.randomUUID();
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restId, "Small Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity table1 = new TableInventoryViewEntity(UUID.randomUUID(), restId, "T1", 4);
        TableInventoryViewEntity table2 = new TableInventoryViewEntity(UUID.randomUUID(), restId, "T2", 6);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of(table1, table2));
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(scheduleClient.isClosedAt(eq(restId), any(LocalDate.class), any(LocalTime.class))).thenReturn(false);

        // Total capacity is 4 + 6 = 10; requesting 12
        LocalDate targetDate = LocalDate.now().plusDays(8);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restId, targetDate, LocalTime.of(19, 0), 12
        );

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.isClosed()).isFalse();
        assertThat(result.reason()).isEqualTo("EXCEEDS_TOTAL_CAPACITY");
        assertThat(result.totalRestaurantCapacity()).isEqualTo(10);
    }
}
