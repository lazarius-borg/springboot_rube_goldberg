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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationListingEnrichedTest {

    private MockMvc mockMvc;
    @Mock private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReservationController(reservationService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void shouldReturnReservationsEnrichedWithTableLabelsAndCancellationReason() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();

        ReservationEntity res = new ReservationEntity(
                resId, restId, UUID.randomUUID(), "Alice", "alice@example.com",
                4, Instant.now(), Instant.now().plusSeconds(5400), "CANCELLED",
                2, Instant.now(), Instant.now(), "Guest requested cancellation"
        );

        when(reservationService.listReservationsByRestaurant(eq(restId), any()))
                .thenReturn(new PageImpl<>(List.of(res), PageRequest.of(0, 50), 1));
        when(reservationService.getAllocatedTablesBatch(List.of(resId)))
                .thenReturn(Map.of(resId, List.of(tableId)));
        when(reservationService.getTableLabels(restId))
                .thenReturn(Map.of(tableId, "T10"));

        mockMvc.perform(get("/api/v1/reservations")
                .param("restaurantId", restId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(resId.toString()))
                .andExpect(jsonPath("$.content[0].allocatedTableLabels[0]").value("T10"))
                .andExpect(jsonPath("$.content[0].cancellationReason").value("Guest requested cancellation"));
    }
}
