package nl.invokedynamic.demo.restaurant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.restaurant.client.ReservationClient;
import nl.invokedynamic.demo.restaurant.domain.RestaurantEntity;
import nl.invokedynamic.demo.restaurant.domain.RestaurantTableEntity;
import nl.invokedynamic.demo.restaurant.domain.TableCombinationEntity;
import nl.invokedynamic.demo.restaurant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    @Mock private ReservationClient reservationClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RestaurantService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantService(
                restaurantRepository, openingHoursRepository, tableRepository,
                combinationRepository, outboxRepository, objectMapper, reservationClient
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
        assertThat(table.getZone()).isEqualTo("Main Dining");

        verify(tableRepository).save(any(RestaurantTableEntity.class));
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldAddTableWithFloorZone() {
        UUID restId = UUID.randomUUID();
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());

        RestaurantTableEntity table = service.addTable(restId, "R1", 2, "Rooftop");
        assertThat(table.getTableNumber()).isEqualTo("R1");
        assertThat(table.getCapacity()).isEqualTo(2);
        assertThat(table.getZone()).isEqualTo("Rooftop");

        verify(tableRepository).save(any(RestaurantTableEntity.class));
    }

    @Test
    void shouldRejectZeroOrNegativeCapacity() {
        UUID restId = UUID.randomUUID();
        assertThatThrownBy(() -> service.addTable(restId, "T0", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table capacity must be greater than 0");
    }

    @Test
    void shouldAddTableCombinationWithAutoDefaults() {
        UUID restId = UUID.randomUUID();
        UUID t1Id = UUID.randomUUID();
        UUID t2Id = UUID.randomUUID();
        RestaurantTableEntity t1 = new RestaurantTableEntity(t1Id, restId, "T1", 2, "Main Dining", "ACTIVE", Instant.now());
        RestaurantTableEntity t2 = new RestaurantTableEntity(t2Id, restId, "T2", 4, "Main Dining", "ACTIVE", Instant.now());

        when(tableRepository.findAllById(List.of(t1Id, t2Id))).thenReturn(List.of(t1, t2));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of(t1, t2));
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());

        TableCombinationEntity combo = service.addTableCombination(restId, null, List.of(t1Id, t2Id), null);
        assertThat(combo.getName()).isEqualTo("Combo: T1 + T2");
        assertThat(combo.getCombinedCapacity()).isEqualTo(6);

        verify(combinationRepository).save(any(TableCombinationEntity.class));
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldUpdateTableDetailsAndSyncCombinations() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID otherTableId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(tableId, restId, "T1", 2, "Main Dining", "ACTIVE", Instant.now());
        RestaurantTableEntity otherTable = new RestaurantTableEntity(otherTableId, restId, "T2", 4, "Main Dining", "ACTIVE", Instant.now());

        TableCombinationEntity combo = new TableCombinationEntity(UUID.randomUUID(), restId, "Combo: T1 + T2", List.of(tableId, otherTableId), 6);

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of(combo));
        when(tableRepository.findAllById(List.of(tableId, otherTableId))).thenReturn(List.of(table, otherTable));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of(table, otherTable));

        RestaurantTableEntity updated = service.updateTable(restId, tableId, "T1-Updated", 6, "Patio");
        assertThat(updated.getTableNumber()).isEqualTo("T1-Updated");
        assertThat(updated.getCapacity()).isEqualTo(6);
        assertThat(updated.getZone()).isEqualTo("Patio");
        assertThat(combo.getCombinedCapacity()).isEqualTo(10);

        verify(combinationRepository).save(combo);
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldDeleteTableSafelyWhenNoActiveReservations() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(tableId, restId, "T1", 2, "Main Dining", "ACTIVE", Instant.now());

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(reservationClient.hasActiveUpcomingReservations(restId, tableId)).thenReturn(false);
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of());
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of());

        service.deleteTable(restId, tableId);

        verify(tableRepository).delete(table);
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldRejectDeleteTableWhenActiveReservationsExist() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(tableId, restId, "T1", 2, "Main Dining", "ACTIVE", Instant.now());

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(reservationClient.hasActiveUpcomingReservations(restId, tableId)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteTable(restId, tableId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active upcoming reservations");

        verify(tableRepository, never()).delete(any());
    }

    @Test
    void shouldDissolveCombinationsWhenTableDeleted() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(tableId, restId, "T1", 2, "Main Dining", "ACTIVE", Instant.now());
        TableCombinationEntity combo = new TableCombinationEntity(UUID.randomUUID(), restId, "Combo: T1 + T2", List.of(tableId, UUID.randomUUID()), 6);

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(reservationClient.hasActiveUpcomingReservations(restId, tableId)).thenReturn(false);
        when(combinationRepository.findByRestaurantId(restId)).thenReturn(List.of(combo));
        when(tableRepository.findByRestaurantId(restId)).thenReturn(List.of());

        service.deleteTable(restId, tableId);

        verify(combinationRepository).deleteAll(List.of(combo));
        verify(tableRepository).delete(table);
    }
}
