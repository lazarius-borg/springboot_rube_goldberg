package nl.invokedynamic.demo.restaurant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nl.invokedynamic.demo.restaurant.domain.*;
import nl.invokedynamic.demo.restaurant.service.RestaurantService;
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
    public ResponseEntity<Page<RestaurantEntity>> listRestaurants(Pageable pageable) {
        return ResponseEntity.ok(restaurantService.listRestaurants(pageable));
    }

    @PostMapping
    @Operation(summary = "Register new restaurant", description = "Registers an establishment with booking horizon and cancellation policies.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Restaurant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid restaurant data or invalid timezone")
    })
    public ResponseEntity<?> createRestaurant(@RequestBody CreateRestaurantRequest req) {
        try {
            RestaurantEntity entity = restaurantService.createRestaurant(
                    req.name(), req.address(), req.timezone(),
                    req.defaultReservationDurationMinutes() > 0 ? req.defaultReservationDurationMinutes() : 90,
                    req.minBookingAdvanceMinutes() > 0 ? req.minBookingAdvanceMinutes() : 30,
                    req.maxBookingHorizonDays() > 0 ? req.maxBookingHorizonDays() : 60,
                    req.cancellationWindowHours() > 0 ? req.cancellationWindowHours() : 2
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/invalid-restaurant"));
            pd.setTitle("Invalid Restaurant Data");
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
    public ResponseEntity<?> addTable(@PathVariable UUID id, @RequestBody CreateTableRequest req) {
        try {
            RestaurantTableEntity table = restaurantService.addTable(id, req.tableNumber(), req.capacity());
            return ResponseEntity.status(HttpStatus.CREATED).body(table);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @GetMapping("/{id}/tables")
    @Operation(summary = "List restaurant tables")
    public ResponseEntity<List<RestaurantTableEntity>> getTables(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getTables(id));
    }

    @PostMapping("/{id}/table-combinations")
    @Operation(summary = "Define combinable tables", description = "Creates a combined table configuration from multiple existing tables.")
    public ResponseEntity<TableCombinationEntity> addCombination(@PathVariable UUID id, @RequestBody CreateCombinationRequest req) {
        TableCombinationEntity comb = restaurantService.addTableCombination(id, req.name(), req.tableIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(comb);
    }

    @PutMapping("/{id}/opening-hours")
    @Operation(summary = "Configure opening hours schedule")
    public ResponseEntity<?> configureOpeningHours(@PathVariable UUID id, @RequestBody OpeningHoursConfigDto dto) {
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

    public record CreateRestaurantRequest(String name, String address, String timezone,
                                          int defaultReservationDurationMinutes, int minBookingAdvanceMinutes,
                                          int maxBookingHorizonDays, int cancellationWindowHours) {}
    public record CreateTableRequest(String tableNumber, int capacity) {}
    public record CreateCombinationRequest(String name, List<UUID> tableIds) {}
    public record OpeningHoursConfigDto(List<ScheduleItemDto> schedules) {}
    public record ScheduleItemDto(Integer dayOfWeek, LocalDate specificDate, LocalTime openTime, LocalTime closeTime, boolean isClosed) {}
}
