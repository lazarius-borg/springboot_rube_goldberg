package nl.invokedynamic.demo.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.reservation.client.RestaurantClient;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationDurationTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationTableAllocationRepository allocationRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private TableAllocationEngine allocationEngine;
    @Mock private RestaurantClient restaurantClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        lenient().when(restaurantClient.isWithinOperatingHours(any(), any(), any())).thenReturn(true);
        reservationService = new ReservationService(
                reservationRepository,
                allocationRepository,
                outboxRepository,
                allocationEngine,
                objectMapper,
                restaurantClient
        );
    }

    @Test
    void shouldRejectReservationWhenDurationLessThanMinAllowed() {
        UUID restId = UUID.randomUUID();
        when(restaurantClient.isClosedAt(eq(restId), any())).thenReturn(false);
        when(restaurantClient.getMinReservationDurationMinutes(restId)).thenReturn(45);

        assertThatThrownBy(() -> reservationService.createReservation(
                restId, UUID.randomUUID(), "Alice", "alice@example.com", 2,
                Instant.now(), 30, 2, List.of(), List.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("is less than restaurant minimum duration of 45 minutes");
    }

    @Test
    void shouldRejectReservationWhenDurationExceedsMaxAllowed() {
        UUID restId = UUID.randomUUID();
        when(restaurantClient.isClosedAt(eq(restId), any())).thenReturn(false);
        when(restaurantClient.getMaxReservationDurationMinutes(restId)).thenReturn(120);

        assertThatThrownBy(() -> reservationService.createReservation(
                restId, UUID.randomUUID(), "Alice", "alice@example.com", 2,
                Instant.now(), 150, 2, List.of(), List.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exceeds restaurant maximum duration of 120 minutes");
    }

    @Test
    void shouldBlockTableForFullDuration() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-09-01T18:00:00Z");
        int durationMinutes = 120;
        Instant expectedEndTime = startTime.plusSeconds(120 * 60);

        when(restaurantClient.isClosedAt(eq(restId), any())).thenReturn(false);
        when(restaurantClient.getMaxReservationDurationMinutes(restId)).thenReturn(180);
        when(restaurantClient.getTableCandidates(restId)).thenReturn(List.of(new TableAllocationEngine.TableCandidate(tableId, 4)));
        when(restaurantClient.getCombinationCandidates(restId)).thenReturn(List.of());
        when(allocationEngine.allocateTable(eq(2), any(), any(), any())).thenReturn(Optional.of(List.of(tableId)));

        ReservationEntity created = reservationService.createReservation(
                restId, UUID.randomUUID(), "Alice", "alice@example.com", 2,
                startTime, durationMinutes, 2, List.of(), List.of()
        );

        assertThat(created.getEndTime()).isEqualTo(expectedEndTime);
        verify(reservationRepository).save(argThat(r -> r.getEndTime().equals(expectedEndTime)));
    }

    @Test
    void shouldRejectReservationWhenOutsideOperatingHours() {
        UUID restId = UUID.randomUUID();
        when(restaurantClient.isClosedAt(eq(restId), any())).thenReturn(false);
        when(restaurantClient.isWithinOperatingHours(eq(restId), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> reservationService.createReservation(
                restId, UUID.randomUUID(), "Alice", "alice@example.com", 2,
                Instant.now(), 60, 2, List.of(), List.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("outside restaurant operating hours or extends past closing time");
    }
}
