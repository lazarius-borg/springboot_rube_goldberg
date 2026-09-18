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

        if (bestCombination.isPresent()) {
            return bestCombination.map(CombinationCandidate::tableIds);
        }

        // Rule 3: Multi-Table allocation: allocate multiple distinct free tables whose aggregate capacity >= partySize
        List<TableCandidate> freeTables = allTables.stream()
                .filter(t -> !occupiedTableIds.contains(t.tableId()))
                .sorted(Comparator.comparingInt(TableCandidate::capacity).reversed())
                .toList();

        int totalFreeCapacity = freeTables.stream().mapToInt(TableCandidate::capacity).sum();
        if (totalFreeCapacity < partySize) {
            return Optional.empty();
        }

        return findOptimalMultiTableCombination(partySize, freeTables);
    }

    private Optional<List<UUID>> findOptimalMultiTableCombination(int partySize, List<TableCandidate> freeTables) {
        List<TableCandidate> bestCombo = new ArrayList<>();
        int[] bestWaste = new int[]{Integer.MAX_VALUE};
        int[] bestCount = new int[]{Integer.MAX_VALUE};

        searchCombinations(0, 0, new ArrayList<>(), partySize, freeTables, bestCombo, bestWaste, bestCount);

        if (bestCombo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bestCombo.stream().map(TableCandidate::tableId).toList());
    }

    private void searchCombinations(int index, int currentSum, List<TableCandidate> currentSubset,
                                    int partySize, List<TableCandidate> freeTables,
                                    List<TableCandidate> bestCombo, int[] bestWaste, int[] bestCount) {
        if (currentSum >= partySize) {
            int waste = currentSum - partySize;
            int count = currentSubset.size();
            if (waste < bestWaste[0] || (waste == bestWaste[0] && count < bestCount[0])) {
                bestWaste[0] = waste;
                bestCount[0] = count;
                bestCombo.clear();
                bestCombo.addAll(currentSubset);
            }
            return;
        }

        if (index >= freeTables.size()) {
            return;
        }

        // Pruning: if even taking all remaining tables cannot reach partySize, abort branch
        int remainingSum = 0;
        for (int i = index; i < freeTables.size(); i++) {
            remainingSum += freeTables.get(i).capacity();
        }
        if (currentSum + remainingSum < partySize) {
            return;
        }

        // Option 1: Include freeTables[index]
        currentSubset.add(freeTables.get(index));
        searchCombinations(index + 1, currentSum + freeTables.get(index).capacity(),
                currentSubset, partySize, freeTables, bestCombo, bestWaste, bestCount);
        currentSubset.remove(currentSubset.size() - 1);

        // Option 2: Exclude freeTables[index]
        searchCombinations(index + 1, currentSum, currentSubset,
                partySize, freeTables, bestCombo, bestWaste, bestCount);
    }
}
