package nl.invokedynamic.demo.reservation.api;

import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReservationControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService)).build();
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

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "restaurantId": "%s",
                      "customerName": "Alice",
                      "customerEmail": "alice@example.com",
                      "partySize": 4,
                      "startTime": "2026-09-01T19:00:00Z"
                    }
                """, restId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldReturnConflictWhenBookingFails() throws Exception {
        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("No suitable tables available for party size 4"));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 4,
                      "startTime": "2026-09-01T19:00:00Z"
                    }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reservation Conflict"))
                .andExpect(jsonPath("$.detail").value("No suitable tables available for party size 4"));
    }
}
