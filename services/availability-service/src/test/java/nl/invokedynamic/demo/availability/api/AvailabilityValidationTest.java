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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AvailabilityValidationTest {

    private MockMvc mockMvc;
    @Mock private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AvailabilityController(availabilityService))
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptValidAvailabilityQuery() throws Exception {
        UUID restId = UUID.randomUUID();
        when(availabilityService.checkAvailability(eq(restId), any(LocalDate.class), any(LocalTime.class), anyInt()))
                .thenReturn(new AvailabilityService.AvailabilityResult(
                        restId, "2026-09-01T19:00:00+02:00[Europe/Amsterdam]", 4, true, List.of("2026-09-01T19:00:00+02:00")
                ));

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void shouldRejectPartySizeBelowMinimum() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name", containsString("partySize")));
    }

    @Test
    void shouldRejectPartySizeAboveMaximum() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name", containsString("partySize")));
    }

    @Test
    void shouldReturnNotFoundWhenRestaurantDoesNotExist() throws Exception {
        UUID restId = UUID.randomUUID();
        when(availabilityService.checkAvailability(eq(restId), any(LocalDate.class), any(LocalTime.class), anyInt()))
                .thenThrow(new IllegalArgumentException("Restaurant not found: " + restId));

        mockMvc.perform(get("/api/v1/availability")
                .param("restaurantId", restId.toString())
                .param("date", "2026-09-01")
                .param("time", "19:00:00")
                .param("partySize", "4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail", containsString("Restaurant not found")));
    }
}
