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
            String msg = e.getMessage() != null ? e.getMessage() : "Invalid argument";
            boolean isNotFound = msg.toLowerCase().contains("not found");
            HttpStatus status = isNotFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, msg);
            pd.setType(URI.create(isNotFound ? "https://example.invalid/problems/not-found" : "https://example.invalid/problems/validation-error"));
            pd.setTitle(isNotFound ? "Resource Not Found" : "Validation Failed");
            if (!isNotFound) {
                String paramName = msg.toLowerCase().contains("date") ? "date" : "time";
                pd.setProperty("invalidParams", java.util.List.of(java.util.Map.of("name", paramName, "reason", msg)));
            }
            return ResponseEntity.status(status).body(pd);
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
