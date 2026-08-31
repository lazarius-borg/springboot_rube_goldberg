package nl.invokedynamic.demo.availability.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import nl.invokedynamic.demo.availability.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability")
@Tag(name = "Availability", description = "Real-time table availability queries with sub-50ms Redis caching")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @Operation(summary = "Check table availability", description = "Queries if tables (or combinations) can accommodate the party at the requested time slot.")
    @ApiResponse(responseCode = "200", description = "Availability query processed")
    public ResponseEntity<AvailabilityService.AvailabilityResult> checkAvailability(
            @Parameter(description = "Restaurant UUID", required = true) @RequestParam UUID restaurantId,
            @Parameter(description = "Date of dining (YYYY-MM-DD)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Time of dining (HH:mm:ss)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @Parameter(description = "Number of guests in party") @RequestParam(defaultValue = "2") int partySize) {
        return ResponseEntity.ok(availabilityService.checkAvailability(restaurantId, date, time, partySize));
    }
}
