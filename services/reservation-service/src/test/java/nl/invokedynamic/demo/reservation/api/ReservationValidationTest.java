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
import static org.mockito.ArgumentMatchers.anyInt;
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
    private final java.time.Clock fixedClock = java.time.Clock.fixed(
            Instant.parse("2026-09-01T12:00:00Z"),
            java.time.ZoneOffset.UTC
    );

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReservationController(reservationService, fixedClock))
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

    @Test
    void shouldRejectCustomerPastReservation() throws Exception {
        // Customer or unauthenticated: past timestamp beyond 300s grace window
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 2,
                      "startTime": "2026-09-01T11:54:00Z"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("startTime"))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("Reservation start time cannot be in the past"));
    }

    @Test
    void shouldRejectReservationBeyondHorizon() throws Exception {
        // Future booking beyond 365 days
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "partySize": 2,
                      "startTime": "2027-09-03T12:00:00Z"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("startTime"))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("Reservation start time cannot be more than 365 days in advance"));
    }

    @Test
    void shouldAcceptCustomerReservationWithinGraceWindow() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        Instant graceTime = Instant.parse("2026-09-01T11:56:00Z"); // 4 mins in past, within 5 min grace
        ReservationEntity res = new ReservationEntity(
                resId, restId, UUID.randomUUID(), "Customer", "customer@example.com", 2,
                graceTime, graceTime.plusSeconds(5400), "CONFIRMED", Instant.now(), Instant.now()
        );
        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any()))
                .thenReturn(res);
        when(reservationService.getAllocatedTables(resId)).thenReturn(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "restaurantId": "%s",
                      "partySize": 2,
                      "startTime": "2026-09-01T11:56:00Z"
                    }
                """, restId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(resId.toString()));
    }

    @Test
    void shouldAllowManagerToBackfillPastReservation() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        Instant pastTime = Instant.parse("2020-01-01T19:00:00Z");
        ReservationEntity res = new ReservationEntity(
                resId, restId, UUID.randomUUID(), "Backfill Guest", "backfill@example.com", 2,
                pastTime, pastTime.plusSeconds(5400), "CONFIRMED", Instant.now(), Instant.now()
        );
        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), any(), any()))
                .thenReturn(res);
        when(reservationService.getAllocatedTables(resId)).thenReturn(List.of(UUID.randomUUID()));

        // Set security context with ROLE_RESTAURANT_MANAGER
        org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "bob", "password", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RESTAURANT_MANAGER"))
        ));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        try {
            mockMvc.perform(post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("""
                        {
                          "restaurantId": "%s",
                          "partySize": 2,
                          "startTime": "2020-01-01T19:00:00Z"
                        }
                    """, restId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(resId.toString()));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
