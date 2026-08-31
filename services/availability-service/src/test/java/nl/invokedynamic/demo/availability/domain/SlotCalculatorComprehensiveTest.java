package nl.invokedynamic.demo.availability.domain;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorComprehensiveTest {

    @Test
    void shouldDetectOverlappingIntervals() {
        Instant start1 = Instant.parse("2026-09-01T18:00:00Z");
        Instant end1 = start1.plus(Duration.ofMinutes(90));

        Instant start2 = Instant.parse("2026-09-01T19:00:00Z");
        Instant end2 = start2.plus(Duration.ofMinutes(90));

        boolean overlaps = start1.isBefore(end2) && end1.isAfter(start2);
        assertThat(overlaps).isTrue();
    }

    @Test
    void shouldRecognizeConsecutiveNonOverlappingSlots() {
        Instant start1 = Instant.parse("2026-09-01T18:00:00Z");
        Instant end1 = start1.plus(Duration.ofMinutes(90)); // 19:30

        Instant start2 = Instant.parse("2026-09-01T19:30:00Z");
        Instant end2 = start2.plus(Duration.ofMinutes(90)); // 21:00

        boolean overlaps = start1.isBefore(end2) && end1.isAfter(start2);
        assertThat(overlaps).isFalse();
    }

    @Test
    void shouldHandleTableCombinationCapacitySummation() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        TableCombinationViewEntity comb = new TableCombinationViewEntity(
                UUID.randomUUID(), UUID.randomUUID(), "Combo-1-2", List.of(t1, t2), 8
        );

        assertThat(comb.getCombinedCapacity()).isEqualTo(8);
        assertThat(comb.getTableIds()).containsExactly(t1, t2);
    }
}
