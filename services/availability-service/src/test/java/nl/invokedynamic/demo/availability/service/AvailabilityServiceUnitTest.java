package nl.invokedynamic.demo.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AvailabilityService(
                restaurantRepository, tableRepository, combinationRepository,
                occupancyRepository, redisTemplate, objectMapper
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

        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restId, LocalDate.of(2026, 9, 1), LocalTime.of(19, 0), 4
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

        AvailabilityService.AvailabilityResult result = service.checkAvailability(
                restId, LocalDate.of(2026, 9, 1), LocalTime.of(19, 0), 4
        );

        assertThat(result.isAvailable()).isFalse();
    }
}
