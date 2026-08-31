package nl.invokedynamic.demo.restaurant.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantTableTest {

    @Test
    void shouldCreateValidTable() {
        UUID restaurantId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(
                UUID.randomUUID(), restaurantId, "T1", 4, "ACTIVE", Instant.now()
        );

        assertThat(table.getTableNumber()).isEqualTo("T1");
        assertThat(table.getCapacity()).isEqualTo(4);
        assertThat(table.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldCalculateCombinedCapacityCorrectly() {
        UUID restaurantId = UUID.randomUUID();
        UUID table1 = UUID.randomUUID();
        UUID table2 = UUID.randomUUID();

        TableCombinationEntity combination = new TableCombinationEntity(
                UUID.randomUUID(), restaurantId, "T1+T2", List.of(table1, table2), 8
        );

        assertThat(combination.getName()).isEqualTo("T1+T2");
        assertThat(combination.getTableIds()).containsExactly(table1, table2);
        assertThat(combination.getCombinedCapacity()).isEqualTo(8);
    }
}
