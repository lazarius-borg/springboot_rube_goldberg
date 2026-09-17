package nl.invokedynamic.demo.reservation.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TableAllocationEngineTest {

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
    void shouldFallbackToConfiguredCombinationBeforeMultiTable() {
        UUID t1 = UUID.randomUUID(); // cap 4
        UUID t2 = UUID.randomUUID(); // cap 4
        UUID combId = UUID.randomUUID();

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 4),
                new TableAllocationEngine.TableCandidate(t2, 4)
        );
        List<TableAllocationEngine.CombinationCandidate> combinations = List.of(
                new TableAllocationEngine.CombinationCandidate(combId, List.of(t1, t2), 8)
        );

        Optional<List<UUID>> allocated = allocator.allocateTable(8, tables, combinations, Set.of());
        assertThat(allocated).isPresent();
        assertThat(allocated.get()).containsExactly(t1, t2);
    }

    @Test
    void shouldAllocateMultipleTablesForLargePartyWhenNoSingleCombinationFits() {
        // Restaurant with max single table 6, max combination 8, but customer party size is 12
        UUID t1 = UUID.randomUUID(); // cap 6
        UUID t2 = UUID.randomUUID(); // cap 4
        UUID t3 = UUID.randomUUID(); // cap 4
        UUID t4 = UUID.randomUUID(); // cap 2

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 6),
                new TableAllocationEngine.TableCandidate(t2, 4),
                new TableAllocationEngine.TableCandidate(t3, 4),
                new TableAllocationEngine.TableCandidate(t4, 2)
        );
        // Configured combination is only 8 seats
        List<TableAllocationEngine.CombinationCandidate> combinations = List.of(
                new TableAllocationEngine.CombinationCandidate(UUID.randomUUID(), List.of(t1, t4), 8)
        );

        Optional<List<UUID>> allocated = allocator.allocateTable(12, tables, combinations, Set.of());
        assertThat(allocated).isPresent();
        // Should allocate tables whose sum >= 12, e.g. [t1, t2, t3] = 14 or optimal combination
        assertThat(allocated.get()).hasSizeGreaterThanOrEqualTo(2);
        int allocatedCap = tables.stream()
                .filter(t -> allocated.get().contains(t.tableId()))
                .mapToInt(TableAllocationEngine.TableCandidate::capacity)
                .sum();
        assertThat(allocatedCap).isGreaterThanOrEqualTo(12);
    }

    @Test
    void shouldRejectAllocationWhenPartySizeExceedsTotalFreeCapacity() {
        UUID t1 = UUID.randomUUID(); // cap 4
        UUID t2 = UUID.randomUUID(); // cap 4
        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 4),
                new TableAllocationEngine.TableCandidate(t2, 4)
        );

        // Total capacity is 8, party size is 10
        Optional<List<UUID>> allocated = allocator.allocateTable(10, tables, List.of(), Set.of());
        assertThat(allocated).isEmpty();
    }

    @Test
    void shouldExcludeCombinationWhenConstituentTableIsOccupied() {
        UUID t1 = UUID.randomUUID(); // cap 4
        UUID t2 = UUID.randomUUID(); // cap 4
        UUID combId = UUID.randomUUID();

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 4),
                new TableAllocationEngine.TableCandidate(t2, 4)
        );
        List<TableAllocationEngine.CombinationCandidate> combinations = List.of(
                new TableAllocationEngine.CombinationCandidate(combId, List.of(t1, t2), 8)
        );

        // t1 is occupied
        Optional<List<UUID>> allocated = allocator.allocateTable(8, tables, combinations, Set.of(t1));
        // Cannot allocate combination C1 (8) because t1 is occupied, and remaining free cap is only 4
        assertThat(allocated).isEmpty();
    }

    @Test
    void shouldSelectAlternativeCombinationWhenFirstCombinationHasOccupiedConstituent() {
        UUID t1 = UUID.randomUUID(); // cap 4
        UUID t2 = UUID.randomUUID(); // cap 4
        UUID t3 = UUID.randomUUID(); // cap 4
        UUID c1Id = UUID.randomUUID();
        UUID c2Id = UUID.randomUUID();

        List<TableAllocationEngine.TableCandidate> tables = List.of(
                new TableAllocationEngine.TableCandidate(t1, 4),
                new TableAllocationEngine.TableCandidate(t2, 4),
                new TableAllocationEngine.TableCandidate(t3, 4)
        );
        // Partially overlapping combinations: C1 = (T1, T2), C2 = (T2, T3)
        List<TableAllocationEngine.CombinationCandidate> combinations = List.of(
                new TableAllocationEngine.CombinationCandidate(c1Id, List.of(t1, t2), 8),
                new TableAllocationEngine.CombinationCandidate(c2Id, List.of(t2, t3), 8)
        );

        // t1 is occupied -> C1 is unavailable, but C2 (t2, t3) is fully free
        Optional<List<UUID>> allocated = allocator.allocateTable(8, tables, combinations, Set.of(t1));
        assertThat(allocated).isPresent();
        assertThat(allocated.get()).containsExactly(t2, t3);
    }
}
