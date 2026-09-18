package nl.invokedynamic.demo.restaurant.api;

import nl.invokedynamic.demo.restaurant.domain.OpeningHoursEntity;
import nl.invokedynamic.demo.restaurant.domain.RestaurantEntity;
import nl.invokedynamic.demo.restaurant.domain.RestaurantTableEntity;
import nl.invokedynamic.demo.restaurant.domain.TableCombinationEntity;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantValidationTest {

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
    void shouldAcceptValidRestaurantRegistration() throws Exception {
        UUID id = UUID.randomUUID();
        RestaurantEntity entity = new RestaurantEntity(
                id, "Valid Bistro", "123 Main St", "Europe/Amsterdam", 90, 180, 30, 60, 2, "ACTIVE", Instant.now(), Instant.now()
        );
        when(restaurantService.createRestaurant(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(entity);

        mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Valid Bistro",
                        "address": "123 Main St",
                        "timezone": "Europe/Amsterdam",
                        "defaultReservationDurationMinutes": 90,
                        "minBookingAdvanceMinutes": 30,
                        "maxBookingHorizonDays": 60,
                        "cancellationWindowHours": 2
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Valid Bistro"));
    }

    @Test
    void shouldRejectBlankRestaurantNameAndAddress() throws Exception {
        mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "   ",
                        "address": "",
                        "timezone": "Europe/Amsterdam"
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItems("name", "address")));
    }

    @Test
    void shouldRejectInvalidRestaurantBounds() throws Exception {
        mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Extreme Bistro",
                        "address": "123 High St",
                        "timezone": "Europe/Amsterdam",
                        "defaultReservationDurationMinutes": 10,
                        "minBookingAdvanceMinutes": -5,
                        "maxBookingHorizonDays": 1000,
                        "cancellationWindowHours": 200
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItems(
                        "defaultReservationDurationMinutes",
                        "minBookingAdvanceMinutes",
                        "maxBookingHorizonDays",
                        "cancellationWindowHours"
                )));
    }

    @Test
    void shouldRejectInvalidTableCapacity() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "tableNumber": "T1",
                        "capacity": 0
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("capacity"));

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "tableNumber": "T2",
                        "capacity": 51
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("capacity"));
    }

    @Test
    void shouldRejectCombinationWithFewerThanTwoTables() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/table-combinations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Solo Combo",
                        "tableIds": ["00000000-0000-0000-0000-000000000001"]
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("tableIds"));
    }

    @Test
    void shouldRejectCombinationWithDuplicateTables() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/table-combinations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Duplicate Combo",
                        "tableIds": [
                            "00000000-0000-0000-0000-000000000001",
                            "00000000-0000-0000-0000-000000000001"
                        ]
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("tableIds"));
    }

    @Test
    void shouldRejectOpeningHoursWithInvalidDayOfWeek() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/restaurants/" + restId + "/opening-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "schedules": [
                            {
                                "dayOfWeek": 8,
                                "openTime": "09:00:00",
                                "closeTime": "22:00:00",
                                "isClosed": false
                            }
                        ]
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name", containsString("dayOfWeek")));
    }

    @Test
    void shouldRejectOpeningHoursWhenCloseTimeNotAfterOpenTime() throws Exception {
        UUID restId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/restaurants/" + restId + "/opening-hours")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "schedules": [
                            {
                                "dayOfWeek": 1,
                                "openTime": "22:00:00",
                                "closeTime": "09:00:00",
                                "isClosed": false
                            }
                        ]
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name", containsString("closeTime")));
    }
}
