package nl.invokedynamic.demo.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.availability.domain.*;
import nl.invokedynamic.demo.availability.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityCombinationTest {

    @Mock private RestaurantViewRepository restaurantRepository;
    @Mock private TableInventoryViewRepository tableRepository;
    @Mock private TableCombinationViewRepository combinationRepository;
    @Mock private SlotOccupancyViewRepository occupancyRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private AvailabilityService service;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID tableId1 = UUID.randomUUID();
    private final UUID tableId2 = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

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
    @DisplayName("Total restaurant capacity invariant: combinations never increase total physical capacity")
    void shouldNotIncreaseTotalRestaurantCapacityWhenCombinationsExist() {
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restaurantId, "The Grand Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity t1 = new TableInventoryViewEntity(tableId1, restaurantId, "T1", 4);
        TableInventoryViewEntity t2 = new TableInventoryViewEntity(tableId2, restaurantId, "T2", 4);
        TableCombinationViewEntity c1 = new TableCombinationViewEntity(comboId, restaurantId, "Combo T1+T2", List.of(tableId1, tableId2), 8);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(t1, t2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(c1));

        // Party size 9 exceeds sum of tables (4 + 4 = 8), even though combination exists
        LocalDate targetDate = LocalDate.now().plusDays(2);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restaurantId, targetDate, LocalTime.of(19, 0), 9
        );

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.reason()).isEqualTo("EXCEEDS_TOTAL_CAPACITY");
        assertThat(result.totalRestaurantCapacity()).isEqualTo(8); // strictly sum of tables
        assertThat(result.maxTableCapacity()).isEqualTo(8);
    }

    @Test
    @DisplayName("Availability includes combinations when party exceeds single table capacity")
    void shouldReturnAvailableWhenCombinationFitsPartySize() {
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restaurantId, "The Grand Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity t1 = new TableInventoryViewEntity(tableId1, restaurantId, "T1", 4);
        TableInventoryViewEntity t2 = new TableInventoryViewEntity(tableId2, restaurantId, "T2", 4);
        TableCombinationViewEntity c1 = new TableCombinationViewEntity(comboId, restaurantId, "Combo T1+T2", List.of(tableId1, tableId2), 8);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(t1, t2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(c1));
        when(occupancyRepository.findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(restaurantId), any(), any()))
                .thenReturn(List.of());

        // Party size 7 cannot fit T1 (4) or T2 (4), but fits C1 (8)
        LocalDate targetDate = LocalDate.now().plusDays(2);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restaurantId, targetDate, LocalTime.of(19, 0), 7
        );

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.availableSlots()).isNotEmpty();
        assertThat(result.maxTableCapacity()).isEqualTo(8);
    }

    @Test
    @DisplayName("Mutual exclusion: combination is unavailable if any constituent table is occupied")
    void shouldReturnUnavailableWhenConstituentTableIsOccupied() {
        RestaurantViewEntity rest = new RestaurantViewEntity(
                restaurantId, "The Grand Bistro", "Europe/Amsterdam", 90, 30, 60, 2, Instant.now()
        );
        TableInventoryViewEntity t1 = new TableInventoryViewEntity(tableId1, restaurantId, "T1", 4);
        TableInventoryViewEntity t2 = new TableInventoryViewEntity(tableId2, restaurantId, "T2", 4);
        TableCombinationViewEntity c1 = new TableCombinationViewEntity(comboId, restaurantId, "Combo T1+T2", List.of(tableId1, tableId2), 8);

        // T1 is occupied
        SlotOccupancyViewEntity occupancyT1 = new SlotOccupancyViewEntity(
                UUID.randomUUID(), UUID.randomUUID(), restaurantId, tableId1,
                Instant.now(), Instant.now().plusSeconds(5400)
        );

        when(valueOperations.get(anyString())).thenReturn(null);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(rest));
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(t1, t2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(c1));
        when(occupancyRepository.findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(restaurantId), any(), any()))
                .thenReturn(List.of(occupancyT1));

        // Party size 7 requires combination C1, but T1 is occupied, so free capacity is only 4
        LocalDate targetDate = LocalDate.now().plusDays(2);
        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restaurantId, targetDate, LocalTime.of(19, 0), 7
        );

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.reason()).isEqualTo("NO_TABLES_AVAILABLE");
    }

    @Test
    @DisplayName("Cache invalidation clears Redis keys for the restaurant")
    void shouldInvalidateRedisCacheOnConfigurationChange() {
        when(redisTemplate.keys("availability:" + restaurantId + ":*"))
                .thenReturn(Set.of("availability:" + restaurantId + ":2026-10-01:19:00:4"));

        service.invalidateCache(restaurantId);

        verify(redisTemplate).delete(Set.of("availability:" + restaurantId + ":2026-10-01:19:00:4"));
    }
}
