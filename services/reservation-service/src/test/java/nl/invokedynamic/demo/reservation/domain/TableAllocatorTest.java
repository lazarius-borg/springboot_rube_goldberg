package nl.invokedynamic.demo.reservation.domain;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class TableAllocatorTest {

    private final TableAllocationEngine allocator = new TableAllocationEngine();

    @Test
    void shouldPreferSmallestSufficientSingleTable() {
        UUID t1 = UUID.randomUUID(); // cap 2
        UUID t2 = UUID.randomUUID(); // cap 4
        UUID t3 = UUID.randomUUID(); // cap 6

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 2),
                new TableAllocationEngine.TableCandidate(t2, 4),
                new TableAllocationEngine.TableCandidate(t3, 6)
        );

        Optional<List<UUID>> allocated = allocator.allocateTable(4, tables, List.of(), Set.of());
        assertThat(allocated).isPresent();
        assertThat(allocated.get()).containsExactly(t2);
    }

    @Test
    void shouldFallbackToCombinationWhenSingleTableUnavailable() {
        UUID t1 = UUID.randomUUID(); // cap 2
        UUID t2 = UUID.randomUUID(); // cap 2
        UUID combId = UUID.randomUUID();

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 2),
                new TableAllocationEngine.TableCandidate(t2, 2)
        );
        List<TableAllocationEngine.CombinationCandidate> combinations = List.of(
                new TableAllocationEngine.CombinationCandidate(combId, List.of(t1, t2), 4)
        );

        Optional<List<UUID>> allocated = allocator.allocateTable(4, tables, combinations, Set.of());
        assertThat(allocated).isPresent();
        assertThat(allocated.get()).containsExactly(t1, t2);
    }
}
