package nl.invokedynamic.demo.reservation.api;

import nl.invokedynamic.demo.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationDurationWebMvcTest {

    private MockMvc mockMvc;
    @Mock private ReservationService reservationService;
    private final Instant futureStart = Instant.now().plus(Duration.ofDays(1));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReservationController(reservationService))
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectReservationWhenDurationExceedsSchemaBound() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "%s",
                      "customerId": "%s",
                      "customerName": "Alice",
                      "customerEmail": "alice@example.com",
                      "partySize": 2,
                      "startTime": "%s",
                      "durationMinutes": 600,
                      "cancellationWindowHours": 2
                    }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), futureStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("durationMinutes"));
    }

    @Test
    void shouldReturnBadRequestWhenServiceRejectsDurationExceedingRestaurantConfig() throws Exception {
        when(reservationService.createReservation(any(), any(), any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new IllegalArgumentException("Requested duration of 180 minutes exceeds restaurant maximum duration of 120 minutes"));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "%s",
                      "customerId": "%s",
                      "customerName": "Alice",
                      "customerEmail": "alice@example.com",
                      "partySize": 2,
                      "startTime": "%s",
                      "durationMinutes": 180,
                      "cancellationWindowHours": 2
                    }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), futureStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Requested duration of 180 minutes exceeds restaurant maximum duration of 120 minutes"));
    }
}
