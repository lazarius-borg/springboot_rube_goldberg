package nl.invokedynamic.demo.restaurant.api;

import nl.invokedynamic.demo.restaurant.domain.RestaurantEntity;
import nl.invokedynamic.demo.restaurant.domain.RestaurantTableEntity;
import nl.invokedynamic.demo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RestaurantControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantController(restaurantService)).build();
    }

    @Test
    void shouldCreateRestaurantSuccessfully() throws Exception {
        UUID id = UUID.randomUUID();
        RestaurantEntity entity = new RestaurantEntity(
                id, "The Bistro", "Amsterdam", "Europe/Amsterdam", 90, 30, 60, 2, "ACTIVE", Instant.now(), Instant.now()
        );
        when(restaurantService.createRestaurant(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(entity);

        mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "The Bistro",
                      "address": "Amsterdam",
                      "timezone": "Europe/Amsterdam",
                      "defaultReservationDurationMinutes": 90,
                      "minBookingAdvanceMinutes": 30,
                      "maxBookingHorizonDays": 60,
                      "cancellationWindowHours": 2
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("The Bistro"))
                .andExpect(jsonPath("$.timezone").value("Europe/Amsterdam"));
    }

    @Test
    void shouldReturnProblemDetailOnInvalidTimezone() throws Exception {
        when(restaurantService.createRestaurant(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("Invalid IANA timezone: Bad/Zone"));

        mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Bad Zone Restaurant",
                      "address": "Anywhere",
                      "timezone": "Bad/Zone"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Restaurant Data"))
                .andExpect(jsonPath("$.detail").value("Invalid IANA timezone: Bad/Zone"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentRestaurant() throws Exception {
        UUID id = UUID.randomUUID();
        when(restaurantService.getRestaurant(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/restaurants/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void shouldAddTableSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(UUID.randomUUID(), restId, "T1", 4, "ACTIVE", Instant.now());
        when(restaurantService.addTable(restId, "T1", 4)).thenReturn(table);

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "tableNumber": "T1", "capacity": 4 }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("T1"))
                .andExpect(jsonPath("$.capacity").value(4));
    }
}
