package nl.invokedynamic.demo.reservation.api;

import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReservationControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private ReservationService reservationService;
    private final Instant futureStart = Instant.now().plus(Duration.ofDays(1));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void shouldCreateReservationSuccessfully() throws Exception {
        UUID id = UUID.randomUUID();
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        ReservationEntity res = new ReservationEntity(
                id, restId, UUID.randomUUID(), "Alice", "alice@example.com", 4,
                Instant.now(), Instant.now().plusSeconds(5400), "CONFIRMED", Instant.now(), Instant.now()
        );

        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any()))
                .thenReturn(res);
        when(reservationService.getAllocatedTables(id)).thenReturn(List.of(tableId));
        when(reservationService.getTableLabels(restId)).thenReturn(Map.of(tableId, "T4"));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "restaurantId": "%s",
                      "customerName": "Alice",
                      "customerEmail": "alice@example.com",
                      "partySize": 4,
                      "startTime": "%s"
                    }
                """, restId, futureStart)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.allocatedTableLabels[0]").value("T4"));
    }

    @Test
    void shouldCreateMultiTableReservationSuccessfully() throws Exception {
        UUID id = UUID.randomUUID();
        UUID restId = UUID.randomUUID();
        UUID table1 = UUID.randomUUID();
        UUID table2 = UUID.randomUUID();
        ReservationEntity res = new ReservationEntity(
                id, restId, UUID.randomUUID(), "Big Group", "group@example.com", 12,
                Instant.now(), Instant.now().plusSeconds(5400), "CONFIRMED", Instant.now(), Instant.now()
        );

        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any()))
                .thenReturn(res);
        when(reservationService.getAllocatedTables(id)).thenReturn(List.of(table1, table2));
        when(reservationService.getTableLabels(restId)).thenReturn(Map.of(table1, "T1", table2, "T2"));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "restaurantId": "%s",
                      "customerName": "Big Group",
                      "customerEmail": "group@example.com",
                      "partySize": 12,
                      "startTime": "%s"
                    }
                """, restId, futureStart)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partySize").value(12))
                .andExpect(jsonPath("$.allocatedTableIds.length()").value(2))
                .andExpect(jsonPath("$.allocatedTableLabels[0]").value("T1"))
                .andExpect(jsonPath("$.allocatedTableLabels[1]").value("T2"));
    }

    @Test
    void shouldReturnConflictWhenBookingExceedsTimeslotCapacity() throws Exception {
        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("Timeslot capacity exceeded: current overlapping reservations (18) + requested party (4) exceeds total capacity (20)"));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 4,
                      "startTime": "%s"
                    }
                """, futureStart)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reservation Conflict"))
                .andExpect(jsonPath("$.detail").value("Timeslot capacity exceeded: current overlapping reservations (18) + requested party (4) exceeds total capacity (20)"));
    }

    @Test
    void shouldCheckIfTableHasActiveReservations() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(reservationService.hasActiveReservationsForTable(tableId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/reservations/tables/" + tableId + "/has-active"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void shouldListReservationsEnrichedWithTableLabelsAndCancellationReason() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        ReservationEntity res = new ReservationEntity(
                resId, restId, UUID.randomUUID(), "Alice", "alice@example.com", 2,
                Instant.now(), Instant.now().plusSeconds(3600), "CANCELLED", 2, Instant.now(), Instant.now()
        );
        res.setCancellationReason("Customer schedule conflict");

        when(reservationService.listReservationsByRestaurant(eq(restId), any()))
                .thenReturn(new PageImpl<>(List.of(res), PageRequest.of(0, 10), 1));
        when(reservationService.getAllocatedTablesBatch(any())).thenReturn(Map.of(resId, List.of(tableId)));
        when(reservationService.getTableLabels(restId)).thenReturn(Map.of(tableId, "T1"));

        mockMvc.perform(get("/api/v1/reservations?restaurantId=" + restId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(resId.toString()))
                .andExpect(jsonPath("$.content[0].allocatedTableLabels[0]").value("T1"))
                .andExpect(jsonPath("$.content[0].cancellationReason").value("Customer schedule conflict"));
    }
}
