package nl.invokedynamic.demo.reservation.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStateMachineTest {

    @Test
    void shouldInitializeInConfirmedState() {
        ReservationEntity res = new ReservationEntity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Alice", "alice@example.com",
                4, Instant.now(), Instant.now().plusSeconds(5400), "CONFIRMED", Instant.now(), Instant.now()
        );

        assertThat(res.getStatus()).isEqualTo("CONFIRMED");
    }
}
