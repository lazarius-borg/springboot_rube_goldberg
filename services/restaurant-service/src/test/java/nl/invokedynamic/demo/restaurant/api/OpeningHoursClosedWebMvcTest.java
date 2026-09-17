package nl.invokedynamic.demo.restaurant.api;

import nl.invokedynamic.demo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpeningHoursClosedWebMvcTest {

    private MockMvc mockMvc;
    @Mock private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RestaurantController(restaurantService))
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptOpeningHoursWithClosedDay() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/restaurants/" + restId + "/opening-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "schedules": [
                            {
                                "dayOfWeek": 1,
                                "isClosed": true
                            },
                            {
                                "dayOfWeek": 2,
                                "openTime": "11:00:00",
                                "closeTime": "22:00:00",
                                "isClosed": false
                            }
                        ]
                    }
                """))
                .andExpect(status().isOk());

        verify(restaurantService).configureOpeningHours(eq(restId), any());
    }
}
