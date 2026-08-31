package nl.invokedynamic.demo.reservation.domain;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TableAllocationEngine {

    public record TableCandidate(UUID tableId, int capacity) {}
    public record CombinationCandidate(UUID combinationId, List<UUID> tableIds, int combinedCapacity) {}

    public Optional<List<UUID>> allocateTable(int partySize,
                                              List<TableCandidate> allTables,
                                              List<CombinationCandidate> combinations,
                                              Set<UUID> occupiedTableIds) {
        // Rule 1: Find smallest sufficient single table that is free
        Optional<TableCandidate> bestSingleTable = allTables.stream()
                .filter(t -> !occupiedTableIds.contains(t.tableId()))
                .filter(t -> t.capacity() >= partySize)
                .min(Comparator.comparingInt(TableCandidate::capacity));

        if (bestSingleTable.isPresent()) {
            return Optional.of(List.of(bestSingleTable.get().tableId()));
        }

        // Rule 2: Fall back to smallest sufficient explicitly configured table combination
        Optional<CombinationCandidate> bestCombination = combinations.stream()
                .filter(c -> c.combinedCapacity() >= partySize)
                .filter(c -> c.tableIds().stream().noneMatch(occupiedTableIds::contains))
                .min(Comparator.comparingInt(CombinationCandidate::combinedCapacity));

        return bestCombination.map(CombinationCandidate::tableIds);
    }
}
