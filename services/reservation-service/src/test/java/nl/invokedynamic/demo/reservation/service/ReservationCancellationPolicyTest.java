package nl.invokedynamic.demo.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine;
import nl.invokedynamic.demo.reservation.repository.OutboxEventRepository;
import nl.invokedynamic.demo.reservation.repository.ReservationRepository;
import nl.invokedynamic.demo.reservation.repository.ReservationTableAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationPolicyTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationTableAllocationRepository allocationRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private TableAllocationEngine allocationEngine;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        reservationService = new ReservationService(
                reservationRepository, allocationRepository, outboxRepository, allocationEngine, objectMapper
        );
    }

    @Test
    void shouldCancelSuccessfullyWhenWithinAuthoritativeWindow() {
        UUID resId = UUID.randomUUID();
        // Reservation starts 5 hours in future, cancellation window is 2 hours
        ReservationEntity res = new ReservationEntity(
                resId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "alice@example.com",
                4, Instant.now().plus(Duration.ofHours(5)), Instant.now().plus(Duration.ofHours(7)),
                "CONFIRMED", 2, Instant.now(), Instant.now()
        );
        when(reservationRepository.findById(resId)).thenReturn(Optional.of(res));
        when(allocationRepository.findByReservationId(resId)).thenReturn(List.of());

        ReservationEntity cancelled = reservationService.cancelReservation(resId, 0, "Change of plans");
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancellationReason()).isEqualTo("Change of plans");
    }

    @Test
    void shouldRejectCancellationWhenPastAuthoritativeWindowEvenIfClientPassesZeroWindow() {
        UUID resId = UUID.randomUUID();
        // Reservation starts in 1 hour, but authoritative window is 2 hours -> deadline passed
        ReservationEntity res = new ReservationEntity(
                resId, UUID.randomUUID(), UUID.randomUUID(), "Alice", "alice@example.com",
                4, Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(3)),
                "CONFIRMED", 2, Instant.now(), Instant.now()
        );
        when(reservationRepository.findById(resId)).thenReturn(Optional.of(res));

        // Client attempts to pass 0 hours notice to bypass restaurant policy
        assertThatThrownBy(() -> reservationService.cancelReservation(resId, 0, "Late cancel"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cancellation deadline has passed");
    }
}
