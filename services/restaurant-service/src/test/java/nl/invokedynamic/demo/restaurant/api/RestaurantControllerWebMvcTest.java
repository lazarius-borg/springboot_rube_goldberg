package nl.invokedynamic.demo.restaurant.api;

import nl.invokedynamic.demo.restaurant.api.dto.TableCombinationResponse;
import nl.invokedynamic.demo.restaurant.api.dto.UpdateRestaurantSettingsRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
                id, "The Bistro", "Amsterdam", "Europe/Amsterdam", 90, 180, 30, 60, 2, "ACTIVE", Instant.now(), Instant.now()
        );
        when(restaurantService.createRestaurant(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(entity);

        mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "The Bistro",
                      "address": "Amsterdam",
                      "timezone": "Europe/Amsterdam",
                      "defaultReservationDurationMinutes": 90,
                      "maxReservationDurationMinutes": 180,
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
        when(restaurantService.createRestaurant(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
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
        RestaurantTableEntity table = new RestaurantTableEntity(UUID.randomUUID(), restId, "T1", 4, "Main Dining", "ACTIVE", Instant.now());
        when(restaurantService.addTable(eq(restId), eq("T1"), eq(4), any())).thenReturn(table);

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "tableNumber": "T1", "capacity": 4 }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("T1"))
                .andExpect(jsonPath("$.capacity").value(4));
    }

    @Test
    void shouldAddTableWithZoneSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        RestaurantTableEntity table = new RestaurantTableEntity(UUID.randomUUID(), restId, "R1", 2, "Rooftop", "ACTIVE", Instant.now());
        when(restaurantService.addTable(restId, "R1", 2, "Rooftop")).thenReturn(table);

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "tableNumber": "R1", "capacity": 2, "zone": "Rooftop" }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("R1"))
                .andExpect(jsonPath("$.zone").value("Rooftop"));
    }

    @Test
    void shouldUpdateTableSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        RestaurantTableEntity updatedTable = new RestaurantTableEntity(tableId, restId, "T1-Updated", 6, "Patio", "ACTIVE", Instant.now());
        when(restaurantService.updateTable(restId, tableId, "T1-Updated", 6, "Patio")).thenReturn(updatedTable);

        mockMvc.perform(put("/api/v1/restaurants/" + restId + "/tables/" + tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "tableNumber": "T1-Updated", "capacity": 6, "zone": "Patio" }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value("T1-Updated"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.zone").value("Patio"));
    }

    @Test
    void shouldDeleteTableSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        doNothing().when(restaurantService).deleteTable(restId, tableId);

        mockMvc.perform(delete("/api/v1/restaurants/" + restId + "/tables/" + tableId))
                .andExpect(status().isNoContent());

        verify(restaurantService).deleteTable(restId, tableId);
    }

    @Test
    void shouldReturnConflictWhenDeletingTableWithActiveReservations() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        doThrow(new IllegalStateException("Table cannot be deleted because it is allocated to active upcoming reservations"))
                .when(restaurantService).deleteTable(restId, tableId);

        mockMvc.perform(delete("/api/v1/restaurants/" + restId + "/tables/" + tableId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Table Allocation Conflict"))
                .andExpect(jsonPath("$.detail").value("Table cannot be deleted because it is allocated to active upcoming reservations"));
    }

    @Test
    void shouldAddTableCombinationSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        TableCombinationEntity combo = new TableCombinationEntity(UUID.randomUUID(), restId, "Combo: T1 + T2", "Main Dining Room", List.of(t1, t2), 8);
        TableCombinationResponse response = new TableCombinationResponse(
                combo.getId(), restId, "Combo: T1 + T2", "Main Dining Room", List.of(t1, t2), List.of("T1", "T2"), 8
        );
        when(restaurantService.addTableCombination(eq(restId), any(), eq(List.of(t1, t2)), eq(8)))
                .thenReturn(combo);
        when(restaurantService.toResponse(combo)).thenReturn(response);

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/table-combinations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "tableIds": ["%s", "%s"],
                      "combinedCapacity": 8
                    }
                """, t1, t2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Combo: T1 + T2"))
                .andExpect(jsonPath("$.zone").value("Main Dining Room"))
                .andExpect(jsonPath("$.combinedCapacity").value(8));
    }

    @Test
    void shouldReturn400WhenAddTableCombinationFailsValidation() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        when(restaurantService.addTableCombination(eq(restId), any(), eq(List.of(t1, t2)), any()))
                .thenThrow(new IllegalArgumentException("All combined tables must reside in the same floor zone"));

        mockMvc.perform(post("/api/v1/restaurants/" + restId + "/table-combinations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "tableIds": ["%s", "%s"]
                    }
                """, t1, t2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("All combined tables must reside in the same floor zone"));
    }

    @Test
    void shouldGetTableCombinationsSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        TableCombinationResponse response = new TableCombinationResponse(
                UUID.randomUUID(), restId, "Combo: T1 + T2", "Main Dining Room", List.of(UUID.randomUUID(), UUID.randomUUID()), List.of("T1", "T2"), 8
        );
        when(restaurantService.getTableCombinationResponses(restId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/restaurants/" + restId + "/table-combinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Combo: T1 + T2"))
                .andExpect(jsonPath("$[0].zone").value("Main Dining Room"))
                .andExpect(jsonPath("$[0].combinedCapacity").value(8));
    }

    @Test
    void shouldDeleteTableCombinationSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID combId = UUID.randomUUID();
        doNothing().when(restaurantService).deleteTableCombination(restId, combId);

        mockMvc.perform(delete("/api/v1/restaurants/" + restId + "/table-combinations/" + combId))
                .andExpect(status().isNoContent());

        verify(restaurantService).deleteTableCombination(restId, combId);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentTableCombination() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID combId = UUID.randomUUID();
        doThrow(new java.util.NoSuchElementException("Not found")).when(restaurantService).deleteTableCombination(restId, combId);

        mockMvc.perform(delete("/api/v1/restaurants/" + restId + "/table-combinations/" + combId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateTableCombinationSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID combId = UUID.randomUUID();
        TableCombinationEntity entity = new TableCombinationEntity(combId, restId, "Updated Combo", "Main Dining Room", List.of(UUID.randomUUID()), 6);
        TableCombinationResponse response = new TableCombinationResponse(
                combId, restId, "Updated Combo", "Main Dining Room", List.of(UUID.randomUUID()), List.of("T1"), 6
        );

        when(restaurantService.updateTableCombination(eq(restId), eq(combId), eq("Updated Combo"), eq(6))).thenReturn(entity);
        when(restaurantService.toResponse(entity)).thenReturn(response);

        mockMvc.perform(put("/api/v1/restaurants/" + restId + "/table-combinations/" + combId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated Combo",
                      "combinedCapacity": 6
                    }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Combo"))
                .andExpect(jsonPath("$.combinedCapacity").value(6));
    }

    @Test
    void shouldUpdateRestaurantSettingsSuccessfully() throws Exception {
        UUID restId = UUID.randomUUID();
        RestaurantEntity entity = new RestaurantEntity(
                restId, "Updated Name", "New Address", "Europe/Amsterdam", 120, 240, 60, 90, 4, "ACTIVE", Instant.now(), Instant.now()
        );
        when(restaurantService.updateRestaurantSettings(eq(restId), any(UpdateRestaurantSettingsRequest.class)))
                .thenReturn(entity);

        mockMvc.perform(put("/api/v1/restaurants/" + restId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated Name",
                      "address": "New Address",
                      "timezone": "Europe/Amsterdam",
                      "defaultReservationDurationMinutes": 120,
                      "maxReservationDurationMinutes": 240,
                      "minBookingAdvanceMinutes": 60,
                      "maxBookingHorizonDays": 90,
                      "cancellationWindowHours": 4
                    }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.defaultReservationDurationMinutes").value(120))
                .andExpect(jsonPath("$.maxReservationDurationMinutes").value(240));
    }
}
