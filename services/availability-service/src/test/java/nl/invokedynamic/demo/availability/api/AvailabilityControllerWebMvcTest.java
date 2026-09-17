package nl.invokedynamic.demo.availability.api;

import nl.invokedynamic.demo.availability.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(availabilityService)).build();
    }

    @Test
    void shouldReturnAvailabilityResult() throws Exception {
        UUID restId = UUID.randomUUID();
        when(availabilityService.checkAvailability(eq(restId), any(LocalDate.class), any(LocalTime.class), anyInt()))
                .thenReturn(new AvailabilityService.AvailabilityResult(
                        restId, "2026-09-01T19:00:00+02:00[Europe/Amsterdam]", 4, true, false, null,
                        List.of("2026-09-01T19:00:00+02:00"), 8, 24
                ));

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(jsonPath("$.isClosed").value(false))
                .andExpect(jsonPath("$.partySize").value(4))
                .andExpect(jsonPath("$.maxTableCapacity").value(8))
                .andExpect(jsonPath("$.totalRestaurantCapacity").value(24));
    }

    @Test
    void shouldReturnClosedStatusWhenRestaurantIsClosed() throws Exception {
        UUID restId = UUID.randomUUID();
        when(availabilityService.checkAvailability(eq(restId), any(LocalDate.class), any(LocalTime.class), anyInt()))
                .thenReturn(new AvailabilityService.AvailabilityResult(
                        restId, "2026-09-01T19:00:00+02:00[Europe/Amsterdam]", 4, false, true, "RESTAURANT_CLOSED",
                        List.of(), 8, 24
                ));

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false))
                .andExpect(jsonPath("$.isClosed").value(true))
                .andExpect(jsonPath("$.reason").value("RESTAURANT_CLOSED"));
    }

    @Test
    void shouldReturnExceedsCapacityStatusWhenPartySizeExceedsTotalCapacity() throws Exception {
        UUID restId = UUID.randomUUID();
        when(availabilityService.checkAvailability(eq(restId), any(LocalDate.class), any(LocalTime.class), anyInt()))
                .thenReturn(new AvailabilityService.AvailabilityResult(
                        restId, "2026-09-01T19:00:00+02:00[Europe/Amsterdam]", 30, false, false, "EXCEEDS_TOTAL_CAPACITY",
                        List.of(), 8, 24
                ));

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false))
                .andExpect(jsonPath("$.reason").value("EXCEEDS_TOTAL_CAPACITY"))
                .andExpect(jsonPath("$.totalRestaurantCapacity").value(24));
    }
}
