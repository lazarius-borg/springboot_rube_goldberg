package nl.invokedynamic.demo.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine;
import nl.invokedynamic.demo.reservation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceUnitTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationTableAllocationRepository allocationRepository;
    @Mock private OutboxEventRepository outboxRepository;

    private final TableAllocationEngine allocationEngine = new TableAllocationEngine();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(
                reservationRepository, allocationRepository, outboxRepository, allocationEngine, objectMapper
        );
    }

    @Test
    void shouldCreateReservationAndPersistOutbox() {
        UUID restId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();

        when(allocationRepository.findOccupiedTableIds(eq(restId), any(), any())).thenReturn(List.of());

        ReservationEntity res = service.createReservation(
                restId, custId, "Alice", "alice@example.com", 4, Instant.now().plus(Duration.ofDays(1)), 90,
                List.of(new TableAllocationEngine.TableCandidate(tableId, 4)), List.of()
        );

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo("CONFIRMED");
        verify(reservationRepository).save(any());
        verify(allocationRepository).saveAll(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldThrowConflictWhenNoTableAvailable() {
        UUID restId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();

        when(allocationRepository.findOccupiedTableIds(eq(restId), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createReservation(
                restId, custId, "Alice", "alice@example.com", 4, Instant.now().plus(Duration.ofDays(1)), 90,
                List.of(), List.of()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No suitable tables available");
    }

    @Test
    void shouldRejectCancellationWhenDeadlinePassed() {
        UUID resId = UUID.randomUUID();
        ReservationEntity res = new ReservationEntity(
                resId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "alice@example.com", 4,
                Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(3)),
                "CONFIRMED", Instant.now(), Instant.now()
        );

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(res));

        assertThatThrownBy(() -> service.cancelReservation(resId, 2, "Late cancel"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cancellation deadline has passed");
    }

    @Test
    void shouldUpdateStatusOnValidTransition() {
        UUID resId = UUID.randomUUID();
        ReservationEntity res = new ReservationEntity(
                resId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "alice@example.com", 4,
                Instant.now().plus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(1)).plusSeconds(5400),
                "CONFIRMED", Instant.now(), Instant.now()
        );

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(res));

        ReservationEntity updated = service.updateStatus(resId, "ARRIVED");
        assertThat(updated.getStatus()).isEqualTo("ARRIVED");
        verify(outboxRepository).save(any());
    }
}
