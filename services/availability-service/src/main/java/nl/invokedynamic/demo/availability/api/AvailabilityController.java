package nl.invokedynamic.demo.availability.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import nl.invokedynamic.demo.availability.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import java.net.URI;

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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability query processed"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters"),
            @ApiResponse(responseCode = "404", description = "Restaurant not found")
    })
    public ResponseEntity<?> checkAvailability(@Valid @ModelAttribute AvailabilityQuery query) {
        int partySize = query.partySize() != null ? query.partySize() : 2;
        try {
            return ResponseEntity.ok(availabilityService.checkAvailability(query.restaurantId(), query.date(), query.time(), partySize));
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/not-found"));
            pd.setTitle("Resource Not Found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
        }
    }

    public record AvailabilityQuery(
            @NotNull
            @Parameter(description = "Restaurant UUID", required = true)
            UUID restaurantId,

            @NotNull
            @Parameter(description = "Date of dining (YYYY-MM-DD)", required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @NotNull
            @Parameter(description = "Time of dining (HH:mm:ss)", required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
            LocalTime time,

            @Min(1) @Max(50)
            @Parameter(description = "Number of guests in party (1-50)")
            Integer partySize
    ) {}
}
