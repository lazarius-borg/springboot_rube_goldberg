package nl.invokedynamic.demo.availability.domain;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    @Test
    void shouldCalculateSlotOverlapCorrectly() {
        Instant t1 = Instant.parse("2026-09-01T19:00:00Z");
        Instant t1End = t1.plus(Duration.ofMinutes(90));

        Instant t2 = Instant.parse("2026-09-01T20:00:00Z");
        Instant t2End = t2.plus(Duration.ofMinutes(90));

        boolean overlaps = t1.isBefore(t2End) && t1End.isAfter(t2);
        assertThat(overlaps).isTrue();

        Instant t3 = Instant.parse("2026-09-01T20:30:00Z");
        Instant t3End = t3.plus(Duration.ofMinutes(90));

        boolean overlapsAdjacent = t1.isBefore(t3End) && t1End.isAfter(t3);
        assertThat(overlapsAdjacent).isFalse();
    }
}
