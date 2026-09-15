package nl.invokedynamic.demo.analytics.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import nl.invokedynamic.demo.analytics.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Real-time reservation counts, cancellations, no-shows, and waiting list conversion rate KPIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get aggregated analytics summary", description = "Returns total reservations, cancellations, no-shows, average party size, cancellation rate, cancellation category breakdown, party size distribution, and waiting list conversion rate.")
    @ApiResponse(responseCode = "200", description = "Analytics summary calculated")
    public ResponseEntity<AnalyticsService.AnalyticsSummary> getSummary(
            @Parameter(description = "Optional Restaurant UUID filter") @RequestParam(required = false) UUID restaurantId) {
        return ResponseEntity.ok(analyticsService.getSummary(restaurantId));
    }
}
