package nl.invokedynamic.demo.restaurant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.restaurant.client.ReservationClient;
import nl.invokedynamic.demo.restaurant.domain.RestaurantTableEntity;
import nl.invokedynamic.demo.restaurant.domain.TableCombinationEntity;
import nl.invokedynamic.demo.restaurant.repository.OpeningHoursRepository;
import nl.invokedynamic.demo.restaurant.repository.OutboxEventRepository;
import nl.invokedynamic.demo.restaurant.repository.RestaurantRepository;
import nl.invokedynamic.demo.restaurant.repository.RestaurantTableRepository;
import nl.invokedynamic.demo.restaurant.repository.TableCombinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceLifecycleTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private OpeningHoursRepository openingHoursRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private TableCombinationRepository combinationRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private ReservationClient reservationClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private RestaurantService service;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID table1Id = UUID.randomUUID();
    private final UUID table2Id = UUID.randomUUID();
    private final UUID combId = UUID.randomUUID();

    private RestaurantTableEntity table1;
    private RestaurantTableEntity table2;
    private TableCombinationEntity combination;

    @BeforeEach
    void setUp() {
        service = new RestaurantService(
                restaurantRepository,
                openingHoursRepository,
                tableRepository,
                combinationRepository,
                outboxRepository,
                objectMapper,
                reservationClient
        );

        table1 = new RestaurantTableEntity(table1Id, restaurantId, "T1", 4, "Main Dining Room");
        table2 = new RestaurantTableEntity(table2Id, restaurantId, "T2", 4, "Main Dining Room");
        combination = new TableCombinationEntity(combId, restaurantId, "Combo: T1 + T2", "Main Dining Room", List.of(table1Id, table2Id), 8);
    }

    @Test
    @DisplayName("Should successfully delete table combination and emit event")
    void shouldDeleteTableCombinationSuccessfully() {
        when(combinationRepository.findByIdAndRestaurantId(combId, restaurantId))
                .thenReturn(Optional.of(combination));
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(table1, table2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of());

        service.deleteTableCombination(restaurantId, combId);

        verify(combinationRepository).delete(combination);
        verify(outboxRepository).save(any());
    }

    @Test
    @DisplayName("Should cascade delete affected combinations when physical table is deleted")
    void shouldCascadeDeleteCombinationsWhenTableDeleted() {
        when(tableRepository.findById(table1Id)).thenReturn(Optional.of(table1));
        when(reservationClient.hasActiveUpcomingReservations(restaurantId, table1Id)).thenReturn(false);
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(combination));
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(table2));

        service.deleteTable(restaurantId, table1Id);

        verify(combinationRepository).deleteAll(List.of(combination));
        verify(tableRepository).delete(table1);
        verify(outboxRepository).save(any());
    }

    @Test
    @DisplayName("Should prune combinations when table zone changes causing cross-zone configuration")
    void shouldPruneCombinationsWhenZoneChangesToCrossZone() {
        when(tableRepository.findById(table1Id)).thenReturn(Optional.of(table1));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(combination));
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(table1, table2));

        // Update table1 zone to Patio
        service.updateTable(restaurantId, table1Id, null, null, "Patio");

        assertThat(table1.getZone()).isEqualTo("Patio");
        verify(combinationRepository).deleteAll(List.of(combination));
        verify(outboxRepository).save(any());
    }

    @Test
    @DisplayName("Should successfully update table combination name and capacity within limit")
    void shouldUpdateTableCombinationSuccessfully() {
        when(combinationRepository.findByIdAndRestaurantId(combId, restaurantId))
                .thenReturn(Optional.of(combination));
        when(tableRepository.findAllById(List.of(table1Id, table2Id)))
                .thenReturn(List.of(table1, table2));
        when(combinationRepository.save(combination)).thenReturn(combination);
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(table1, table2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(combination));

        TableCombinationEntity updated = service.updateTableCombination(restaurantId, combId, "Custom Combo Name", 6);

        assertThat(updated.getName()).isEqualTo("Custom Combo Name");
        assertThat(updated.getCombinedCapacity()).isEqualTo(6);
        verify(combinationRepository).save(combination);
        verify(outboxRepository).save(any());
    }

    @Test
    @DisplayName("Should reject update when custom capacity exceeds sum of member tables")
    void shouldRejectUpdateWhenCapacityExceedsTableSum() {
        when(combinationRepository.findByIdAndRestaurantId(combId, restaurantId))
                .thenReturn(Optional.of(combination));
        when(tableRepository.findAllById(List.of(table1Id, table2Id)))
                .thenReturn(List.of(table1, table2));

        assertThatThrownBy(() -> service.updateTableCombination(restaurantId, combId, "Name", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed the sum of member table capacities");
    }
}
