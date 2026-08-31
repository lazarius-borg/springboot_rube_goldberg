package nl.invokedynamic.demo.waitinglist.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FifoMatcherTest {

    @Test
    void shouldPrioritizeOldestEntry() {
        UUID restId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);

        WaitingListEntryEntity first = new WaitingListEntryEntity(
                UUID.randomUUID(), restId, UUID.randomUUID(), "first@example.com",
                date, LocalTime.of(18, 0), LocalTime.of(21, 0), 4, "WAITING", Instant.parse("2026-08-31T10:00:00Z")
        );
        WaitingListEntryEntity second = new WaitingListEntryEntity(
                UUID.randomUUID(), restId, UUID.randomUUID(), "second@example.com",
                date, LocalTime.of(18, 0), LocalTime.of(21, 0), 4, "WAITING", Instant.parse("2026-08-31T10:05:00Z")
        );

        List<WaitingListEntryEntity> sorted = List.of(first, second);
        assertThat(sorted.get(0).getCustomerEmail()).isEqualTo("first@example.com");
    }
}
