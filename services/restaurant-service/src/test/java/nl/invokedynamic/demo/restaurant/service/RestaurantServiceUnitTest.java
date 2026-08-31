package nl.invokedynamic.demo.restaurant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.restaurant.domain.RestaurantEntity;
import nl.invokedynamic.demo.restaurant.domain.RestaurantTableEntity;
import nl.invokedynamic.demo.restaurant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceUnitTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private OpeningHoursRepository openingHoursRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private TableCombinationRepository combinationRepository;
    @Mock private OutboxEventRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RestaurantService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantService(
                restaurantRepository, openingHoursRepository, tableRepository,
                combinationRepository, outboxRepository, objectMapper
        );
    }

    @Test
    void shouldCreateRestaurantAndPersistOutboxEvent() {
        RestaurantEntity entity = service.createRestaurant(
                "Le Goldberg", "456 Avenue", "Europe/Paris", 90, 30, 60, 2
        );

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("Le Goldberg");
        verify(restaurantRepository).save(any(RestaurantEntity.class));
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldRejectInvalidTimezone() {
        assertThatThrownBy(() -> service.createRestaurant("Fail Rest", "Address", "Invalid/Timezone", 90, 30, 60, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IANA timezone");
    }

    @Test
    void shouldAddTableAndPublishTableConfigEvent() {
        UUID restId = UUID.randomUUID();
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());

        RestaurantTableEntity table = service.addTable(restId, "T1", 4);
        assertThat(table.getTableNumber()).isEqualTo("T1");
        assertThat(table.getCapacity()).isEqualTo(4);

        verify(tableRepository).save(any(RestaurantTableEntity.class));
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldRejectZeroOrNegativeCapacity() {
        UUID restId = UUID.randomUUID();
        assertThatThrownBy(() -> service.addTable(restId, "T0", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table capacity must be greater than 0");
    }
}
