package nl.invokedynamic.demo.restaurant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import nl.invokedynamic.demo.restaurant.api.dto.CreateCombinationRequest;
import nl.invokedynamic.demo.restaurant.api.dto.CreateRestaurantRequest;
import nl.invokedynamic.demo.restaurant.api.dto.CreateTableRequest;
import nl.invokedynamic.demo.restaurant.api.dto.OpeningHoursConfigDto;
import nl.invokedynamic.demo.restaurant.api.dto.TableCombinationResponse;
import nl.invokedynamic.demo.restaurant.api.dto.UpdateCombinationRequest;
import nl.invokedynamic.demo.restaurant.api.dto.UpdateRestaurantSettingsRequest;
import nl.invokedynamic.demo.restaurant.api.dto.UpdateTableRequest;
import nl.invokedynamic.demo.restaurant.domain.*;
import nl.invokedynamic.demo.restaurant.service.RestaurantService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
@Tag(name = "Restaurants", description = "Restaurant lifecycle, table inventory, and opening hours management")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    @Operation(summary = "List all restaurants", description = "Retrieves a paginated list of registered restaurants.")
    public ResponseEntity<Page<RestaurantEntity>> listRestaurants(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(restaurantService.listRestaurants(pageable));
    }

    @PostMapping
    @Operation(summary = "Register new restaurant", description = "Registers an establishment with booking horizon and cancellation policies.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Restaurant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid restaurant data or invalid timezone")
    })
    public ResponseEntity<?> createRestaurant(@Valid @RequestBody CreateRestaurantRequest req) {
        try {
            RestaurantEntity entity = restaurantService.createRestaurant(
                    req.name(), req.address(), req.timezone(),
                    req.minReservationDurationMinutes() != null ? req.minReservationDurationMinutes() : 45,
                    req.defaultReservationDurationMinutes() != null ? req.defaultReservationDurationMinutes() : 90,
                    req.maxReservationDurationMinutes() != null ? req.maxReservationDurationMinutes() : 180,
                    req.minBookingAdvanceMinutes() != null ? req.minBookingAdvanceMinutes() : 30,
                    req.maxBookingHorizonDays() != null ? req.maxBookingHorizonDays() : 60,
                    req.cancellationWindowHours() != null ? req.cancellationWindowHours() : 2
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/invalid-restaurant"));
            pd.setTitle("Invalid Restaurant Data");
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update establishment settings", description = "Updates restaurant configuration settings while preserving existing confirmed reservations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant settings updated"),
            @ApiResponse(responseCode = "400", description = "Invalid settings data"),
            @ApiResponse(responseCode = "404", description = "Restaurant not found")
    })
    public ResponseEntity<?> updateRestaurantSettings(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRestaurantSettingsRequest req) {
        try {
            RestaurantEntity entity = restaurantService.updateRestaurantSettings(id, req);
            return ResponseEntity.ok(entity);
        } catch (NoSuchElementException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get restaurant details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant found"),
            @ApiResponse(responseCode = "404", description = "Restaurant not found")
    })
    public ResponseEntity<?> getRestaurant(@Parameter(description = "Restaurant UUID") @PathVariable UUID id) {
        Optional<RestaurantEntity> opt = restaurantService.getRestaurant(id);
        if (opt.isPresent()) {
            return ResponseEntity.ok(opt.get());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Restaurant not found: " + id);
        pd.setType(URI.create("https://example.invalid/problems/not-found"));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @PostMapping("/{id}/tables")
    @Operation(summary = "Add dining table", description = "Adds a physical table with designated seating capacity.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Table created"),
            @ApiResponse(responseCode = "400", description = "Invalid table capacity")
    })
    public ResponseEntity<?> addTable(@PathVariable UUID id, @Valid @RequestBody CreateTableRequest req) {
        try {
            RestaurantTableEntity table = restaurantService.addTable(id, req.tableNumber(), req.capacity() != null ? req.capacity() : 2, req.zone());
            return ResponseEntity.status(HttpStatus.CREATED).body(table);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @PutMapping("/{id}/tables/{tableId}")
    @Operation(summary = "Update restaurant table", description = "Updates table label, seating capacity, or floor zone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Table updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid table data"),
            @ApiResponse(responseCode = "404", description = "Table not found")
    })
    public ResponseEntity<?> updateTable(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTableRequest req) {
        try {
            RestaurantTableEntity updated = restaurantService.updateTable(id, tableId, req.tableNumber(), req.capacity(), req.zone());
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @DeleteMapping("/{id}/tables/{tableId}")
    @Operation(summary = "Delete restaurant table", description = "Safely deletes an unused table; rejects deletion if allocated to active upcoming reservations.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Table deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Table not found"),
            @ApiResponse(responseCode = "409", description = "Table has active upcoming reservations")
    })
    public ResponseEntity<?> deleteTable(
            @PathVariable UUID id,
            @PathVariable UUID tableId) {
        try {
            restaurantService.deleteTable(id, tableId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
        } catch (IllegalStateException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/table-has-active-reservations"));
            pd.setTitle("Table Allocation Conflict");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
        }
    }

    @GetMapping("/{id}/tables")
    @Operation(summary = "List restaurant tables")
    public ResponseEntity<List<RestaurantTableEntity>> getTables(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getTables(id));
    }

    @PostMapping("/{id}/table-combinations")
    @Operation(summary = "Define combinable tables", description = "Creates a combined table configuration from multiple existing tables.")
    public ResponseEntity<?> addCombination(@PathVariable UUID id, @Valid @RequestBody CreateCombinationRequest req) {
        try {
            TableCombinationEntity comb = restaurantService.addTableCombination(id, req.name(), req.tableIds(), req.combinedCapacity());
            return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.toResponse(comb));
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @GetMapping("/{id}/table-combinations")
    @Operation(summary = "List table combinations", description = "Retrieves all active table combinations configured for the restaurant.")
    public ResponseEntity<List<TableCombinationResponse>> getTableCombinations(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getTableCombinationResponses(id));
    }

    @PutMapping("/{id}/table-combinations/{combinationId}")
    @Operation(summary = "Update table combination", description = "Updates name and/or capacity of an existing table combination.")
    public ResponseEntity<?> updateTableCombination(
            @PathVariable UUID id,
            @PathVariable UUID combinationId,
            @Valid @RequestBody UpdateCombinationRequest req) {
        try {
            TableCombinationEntity updated = restaurantService.updateTableCombination(id, combinationId, req.name(), req.combinedCapacity());
            return ResponseEntity.ok(restaurantService.toResponse(updated));
        } catch (NoSuchElementException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @DeleteMapping("/{id}/table-combinations/{combinationId}")
    @Operation(summary = "Delete table combination", description = "Deletes a table combination, unpublishing it from future bookings.")
    public ResponseEntity<Void> deleteTableCombination(@PathVariable UUID id, @PathVariable UUID combinationId) {
        try {
            restaurantService.deleteTableCombination(id, combinationId);
            return ResponseEntity.noContent().build();
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/opening-hours")
    @Operation(summary = "Configure opening hours schedule")
    public ResponseEntity<?> configureOpeningHours(@PathVariable UUID id, @Valid @RequestBody OpeningHoursConfigDto dto) {
        List<OpeningHoursEntity> entities = dto.schedules().stream()
                .map(s -> new OpeningHoursEntity(UUID.randomUUID(), id, s.dayOfWeek(), s.specificDate(), s.openTime(), s.closeTime(), s.isClosed()))
                .toList();
        restaurantService.configureOpeningHours(id, entities);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/opening-hours")
    @Operation(summary = "Get opening hours schedule")
    public ResponseEntity<List<OpeningHoursEntity>> getOpeningHours(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getOpeningHours(id));
    }
}
