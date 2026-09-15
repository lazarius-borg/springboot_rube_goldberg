package nl.invokedynamic.demo.restaurant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
                    req.defaultReservationDurationMinutes() != null ? req.defaultReservationDurationMinutes() : 90,
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
            RestaurantTableEntity table = restaurantService.addTable(id, req.tableNumber(), req.capacity() != null ? req.capacity() : 2);
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
    public ResponseEntity<TableCombinationEntity> addCombination(@PathVariable UUID id, @Valid @RequestBody CreateCombinationRequest req) {
        TableCombinationEntity comb = restaurantService.addTableCombination(id, req.name(), req.tableIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(comb);
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

    public record CreateRestaurantRequest(
            @NotBlank @Size(max = 150)
            @Schema(description = "Restaurant trade name", example = "The Bistro", maxLength = 150)
            String name,

            @NotBlank @Size(max = 1000)
            @Schema(description = "Physical address", example = "123 Main St, Amsterdam", maxLength = 1000)
            String address,

            @NotBlank @Size(max = 50)
            @Schema(description = "IANA Timezone identifier", example = "Europe/Amsterdam", maxLength = 50)
            String timezone,

            @Min(15) @Max(480)
            @Schema(description = "Default reservation duration in minutes (15-480)", example = "90", minimum = "15", maximum = "480", defaultValue = "90")
            Integer defaultReservationDurationMinutes,

            @Min(0) @Max(10080)
            @Schema(description = "Minimum booking advance lead time in minutes (0-10080)", example = "30", minimum = "0", maximum = "10080", defaultValue = "30")
            Integer minBookingAdvanceMinutes,

            @Min(1) @Max(365)
            @Schema(description = "Maximum forward booking horizon in days (1-365)", example = "60", minimum = "1", maximum = "365", defaultValue = "60")
            Integer maxBookingHorizonDays,

            @Min(0) @Max(168)
            @Schema(description = "Authoritative cancellation window notice in hours (0-168)", example = "2", minimum = "0", maximum = "168", defaultValue = "2")
            Integer cancellationWindowHours
    ) {}

    public record CreateTableRequest(
            @NotBlank @Size(max = 50)
            @Schema(description = "Table identifier or number", example = "T1", maxLength = 50)
            String tableNumber,

            @NotNull @Min(1) @Max(50)
            @Schema(description = "Physical seating capacity (1-50)", example = "4", minimum = "1", maximum = "50")
            Integer capacity
    ) {}

    public record CreateCombinationRequest(
            @NotBlank @Size(max = 100)
            @Schema(description = "Table combination identifier", example = "Party Hall 1", maxLength = 100)
            String name,

            @NotEmpty @Size(min = 2, max = 10)
            @Schema(description = "Distinct table IDs composing the combination (min 2, max 10)")
            List<UUID> tableIds
    ) {
        @AssertTrue(message = "Table combination must contain at least 2 distinct table identifiers")
        public boolean isTableIds() {
            return tableIds != null && tableIds.stream().distinct().count() == tableIds.size();
        }
    }

    public record OpeningHoursConfigDto(
            @NotEmpty
            @Schema(description = "List of opening schedule items")
            List<@Valid ScheduleItemDto> schedules
    ) {}

    public record ScheduleItemDto(
            @Min(1) @Max(7)
            @Schema(description = "Day of week (1 = Monday, 7 = Sunday)", minimum = "1", maximum = "7")
            Integer dayOfWeek,

            @Schema(description = "Specific calendar date for holiday/exception schedules")
            LocalDate specificDate,

            @NotNull
            @Schema(description = "Daily opening time", example = "09:00:00")
            LocalTime openTime,

            @NotNull
            @Schema(description = "Daily closing time", example = "22:00:00")
            LocalTime closeTime,

            @Schema(description = "Whether the establishment is closed on this schedule day")
            boolean isClosed
    ) {
        @AssertTrue(message = "Close time must be strictly after open time for non-closed days")
        public boolean isCloseTime() {
            if (isClosed) {
                return true;
            }
            return openTime != null && closeTime != null && closeTime.isAfter(openTime);
        }
    }
}
