package nl.invokedynamic.demo.reservation.api;

import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.domain.ReservationStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationValidationTest {

    private MockMvc mockMvc;
    @Mock private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReservationController(reservationService))
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptValidStatusEnumUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationEntity updated = new ReservationEntity(
                id, UUID.randomUUID(), UUID.randomUUID(), "Alice", "alice@example.com", 4,
                Instant.now(), Instant.now().plusSeconds(5400), "ARRIVED", Instant.now(), Instant.now()
        );
        when(reservationService.updateStatus(eq(id), eq(ReservationStatus.ARRIVED))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/reservations/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ARRIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));
    }

    @Test
    void shouldRejectInvalidStatusEnumString() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/reservations/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"UNKNOWN_STATE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid ReservationStatus"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Allowed values")));
    }

    @Test
    void shouldRejectPartySizeBelowMinimum() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 0,
                      "durationMinutes": 90
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("partySize"));
    }

    @Test
    void shouldRejectPartySizeAboveMaximum() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 51,
                      "durationMinutes": 90
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("partySize"));
    }

    @Test
    void shouldRejectDurationAboveMaximum() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 4,
                      "durationMinutes": 500
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("durationMinutes"));
    }

    @Test
    void shouldRejectInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 4,
                      "customerEmail": "not-an-email"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("customerEmail"));
    }
}
