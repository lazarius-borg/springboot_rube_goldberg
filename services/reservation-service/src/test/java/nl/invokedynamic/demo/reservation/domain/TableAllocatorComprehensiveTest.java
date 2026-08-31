package nl.invokedynamic.demo.reservation.domain;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class TableAllocatorComprehensiveTest {

    private final TableAllocationEngine allocator = new TableAllocationEngine();

    @Test
    void shouldRejectPartyWhenNoTablesHaveSufficientCapacity() {
        UUID t1 = UUID.randomUUID(); // capacity 2
        UUID t2 = UUID.randomUUID(); // capacity 2
        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 2),
                new TableAllocationEngine.TableCandidate(t2, 2)
        );

        Optional<List<UUID>> allocated = allocator.allocateTable(6, tables, List.of(), Set.of());
        assertThat(allocated).isEmpty();
    }

    @Test
    void shouldRejectWhenAllSufficientTablesAreOccupied() {
        UUID t1 = UUID.randomUUID(); // capacity 4
        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 4)
        );

        Optional<List<UUID>> allocated = allocator.allocateTable(4, tables, List.of(), Set.of(t1));
        assertThat(allocated).isEmpty();
    }

    @Test
    void shouldPreferSmallestSufficientCombination() {
        UUID t1 = UUID.randomUUID(); // cap 2
        UUID t2 = UUID.randomUUID(); // cap 2
        UUID t3 = UUID.randomUUID(); // cap 4
        UUID c1 = UUID.randomUUID(); // t1+t2 = cap 4
        UUID c2 = UUID.randomUUID(); // t1+t3 = cap 6

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 2),
                new TableAllocationEngine.TableCandidate(t2, 2),
                new TableAllocationEngine.TableCandidate(t3, 4)
        );
        List<TableAllocationEngine.CombinationCandidate> combinations = List.of(
                new TableAllocationEngine.CombinationCandidate(c1, List.of(t1, t2), 4),
                new TableAllocationEngine.CombinationCandidate(c2, List.of(t1, t3), 6)
        );

        // All single tables occupied, party size 4
        Optional<List<UUID>> allocated = allocator.allocateTable(4, tables, combinations, Set.of(t3));
        assertThat(allocated).isPresent();
        assertThat(allocated.get()).containsExactly(t1, t2);
    }
}
