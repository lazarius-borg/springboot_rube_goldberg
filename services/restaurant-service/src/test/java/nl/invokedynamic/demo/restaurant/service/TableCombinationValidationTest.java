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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableCombinationValidationTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private OpeningHoursRepository openingHoursRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private TableCombinationRepository combinationRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private ReservationClient reservationClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private RestaurantService service;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID t1Id = UUID.randomUUID();
    private final UUID t2Id = UUID.randomUUID();
    private final UUID t3Id = UUID.randomUUID();
    private final UUID patioTableId = UUID.randomUUID();

    private RestaurantTableEntity table1;
    private RestaurantTableEntity table2;
    private RestaurantTableEntity table3;
    private RestaurantTableEntity patioTable;

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

        table1 = new RestaurantTableEntity(t1Id, restaurantId, "T1", 4, "Main Dining Room");
        table2 = new RestaurantTableEntity(t2Id, restaurantId, "T2", 4, "Main Dining Room");
        table3 = new RestaurantTableEntity(t3Id, restaurantId, "T3", 2, "Main Dining Room");
        patioTable = new RestaurantTableEntity(patioTableId, restaurantId, "P1", 4, "Patio");
    }

    @Test
    @DisplayName("Should reject combination with fewer than 2 tables")
    void shouldRejectLessThanTwoTables() {
        assertThatThrownBy(() -> service.addTableCombination(restaurantId, "Combo 1", List.of(t1Id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 tables");
    }

    @Test
    @DisplayName("Should reject combination spanning different zones")
    void shouldRejectCrossZoneTables() {
        when(tableRepository.findAllById(List.of(t1Id, patioTableId)))
                .thenReturn(List.of(table1, patioTable));

        assertThatThrownBy(() -> service.addTableCombination(restaurantId, "Cross Zone", List.of(t1Id, patioTableId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same floor zone");
    }

    @Test
    @DisplayName("Should reject duplicate combination regardless of table order")
    void shouldRejectDuplicateCombinationRegardlessOfOrder() {
        TableCombinationEntity existingCombo = new TableCombinationEntity(
                UUID.randomUUID(), restaurantId, "Existing Combo", "Main Dining Room", List.of(t1Id, t2Id), 8
        );
        when(tableRepository.findAllById(List.of(t2Id, t1Id)))
                .thenReturn(List.of(table2, table1));
        when(combinationRepository.findByRestaurantId(restaurantId))
                .thenReturn(List.of(existingCombo));

        // Attempt creation with reversed order [t2, t1]
        assertThatThrownBy(() -> service.addTableCombination(restaurantId, "Reversed Order", List.of(t2Id, t1Id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should allow partial overlapping combinations in same zone")
    void shouldAllowPartialOverlapCombinations() {
        TableCombinationEntity existingCombo = new TableCombinationEntity(
                UUID.randomUUID(), restaurantId, "T1+T2", "Main Dining Room", List.of(t1Id, t2Id), 8
        );
        when(tableRepository.findAllById(List.of(t1Id, t3Id)))
                .thenReturn(List.of(table1, table3));
        when(combinationRepository.findByRestaurantId(restaurantId))
                .thenReturn(List.of(existingCombo));
        when(tableRepository.findByRestaurantId(restaurantId))
                .thenReturn(List.of(table1, table2, table3));

        TableCombinationEntity created = service.addTableCombination(restaurantId, "T1+T3", List.of(t1Id, t3Id));

        assertThat(created).isNotNull();
        assertThat(created.getCombinedCapacity()).isEqualTo(6); // 4 + 2
        assertThat(created.getZone()).isEqualTo("Main Dining Room");
        verify(combinationRepository).save(any(TableCombinationEntity.class));
    }

    @Test
    @DisplayName("Should default capacity to exact sum of constituent tables")
    void shouldDefaultCapacityToSumOfTables() {
        when(tableRepository.findAllById(List.of(t1Id, t2Id))).thenReturn(List.of(table1, table2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of());
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(table1, table2));

        TableCombinationEntity created = service.addTableCombination(restaurantId, null, List.of(t1Id, t2Id), null);

        assertThat(created.getCombinedCapacity()).isEqualTo(8);
        assertThat(created.getName()).isEqualTo("Combo: T1 + T2");
    }

    @Test
    @DisplayName("Should allow custom capacity less than or equal to sum")
    void shouldAllowCustomCapacityLessThanOrEqualToSum() {
        when(tableRepository.findAllById(List.of(t1Id, t2Id))).thenReturn(List.of(table1, table2));
        when(combinationRepository.findByRestaurantId(restaurantId)).thenReturn(List.of());
        when(tableRepository.findByRestaurantId(restaurantId)).thenReturn(List.of(table1, table2));

        TableCombinationEntity created = service.addTableCombination(restaurantId, "Custom Cap", List.of(t1Id, t2Id), 6);

        assertThat(created.getCombinedCapacity()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should reject custom capacity that exceeds sum of constituent tables")
    void shouldRejectCustomCapacityExceedingSum() {
        when(tableRepository.findAllById(List.of(t1Id, t2Id))).thenReturn(List.of(table1, table2));

        assertThatThrownBy(() -> service.addTableCombination(restaurantId, "Too Big", List.of(t1Id, t2Id), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed the sum");
    }
}
