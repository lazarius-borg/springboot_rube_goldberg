package nl.invokedynamic.demo.reservation.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.domain.ReservationStatus;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine;
import nl.invokedynamic.demo.reservation.service.ReservationService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Guaranteed reservation creation, table allocation, lifecycle, and cancellation")
public class ReservationController {

    private final ReservationService reservationService;
    private final java.time.Clock clock;

    public ReservationController(ReservationService reservationService, java.time.Clock clock) {
        this.reservationService = reservationService;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Create guaranteed reservation", description = "Authoritatively allocates optimal table and creates confirmed booking with outbox event.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation confirmed"),
            @ApiResponse(responseCode = "409", description = "Reservation conflict / no tables available"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<?> createReservation(@Valid @RequestBody CreateReservationRequest req) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isManagerOrAdmin = auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_RESTAURANT_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));

            Instant now = clock.instant();
            Instant startTime = req.startTime() != null ? req.startTime() : now;

            if (!isManagerOrAdmin) {
                // Customer or unauthenticated: enforce current/future with 5-minute clock-skew grace period and max 365 days
                if (startTime.isBefore(now.minusSeconds(300))) {
                    throw new IllegalArgumentException("Reservation start time cannot be in the past");
                }
                if (startTime.isAfter(now.plus(Duration.ofDays(365)))) {
                    throw new IllegalArgumentException("Reservation start time cannot be more than 365 days in advance");
                }
            }

            int partySize = req.partySize() != null ? req.partySize() : 2;
            int durationMinutes = req.durationMinutes() != null && req.durationMinutes() > 0 ? req.durationMinutes() : 90;

            List<TableAllocationEngine.TableCandidate> tables = req.availableTables() != null ? req.availableTables() :
                    List.of(new TableAllocationEngine.TableCandidate(UUID.randomUUID(), partySize));
            List<TableAllocationEngine.CombinationCandidate> combinations = req.combinations() != null ? req.combinations() : List.of();

            UUID customerId = req.customerId() != null ? req.customerId() : UUID.randomUUID();
            String customerName = req.customerName() != null && !req.customerName().isBlank() ? req.customerName() : "Customer";
            String customerEmail = req.customerEmail() != null && !req.customerEmail().isBlank() ? req.customerEmail() : "customer@example.com";

            ReservationEntity reservation;
            if (req.cancellationWindowHours() != null && req.cancellationWindowHours() > 0) {
                reservation = reservationService.createReservation(
                        req.restaurantId(), customerId, customerName, customerEmail,
                        partySize, startTime, durationMinutes, req.cancellationWindowHours(),
                        tables, combinations
                );
            } else {
                reservation = reservationService.createReservation(
                        req.restaurantId(), customerId, customerName, customerEmail,
                        partySize, startTime, durationMinutes,
                        tables, combinations
                );
            }

            List<UUID> allocated = reservationService.getAllocatedTables(reservation.getId());
            ReservationResponseDto response = new ReservationResponseDto(
                    reservation.getId(), reservation.getRestaurantId(), reservation.getCustomerId(),
                    reservation.getCustomerName(), reservation.getCustomerEmail(), reservation.getPartySize(),
                    reservation.getStartTime(), reservation.getEndTime(), reservation.getStatus(), allocated
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/reservation-conflict"));
            pd.setTitle("Reservation Conflict");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/validation-error"));
            pd.setTitle("Validation Failed");
            pd.setProperty("invalidParams", List.of(Map.of("name", "startTime", "reason", e.getMessage())));
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reservation by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation details"),
            @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    public ResponseEntity<?> getReservation(@Parameter(description = "Reservation UUID") @PathVariable UUID id) {
        Optional<ReservationEntity> opt = reservationService.getReservation(id);
        if (opt.isPresent()) {
            ReservationEntity res = opt.get();
            List<UUID> allocated = reservationService.getAllocatedTables(id);
            return ResponseEntity.ok(new ReservationResponseDto(
                    res.getId(), res.getRestaurantId(), res.getCustomerId(), res.getCustomerName(),
                    res.getCustomerEmail(), res.getPartySize(), res.getStartTime(), res.getEndTime(),
                    res.getStatus(), allocated
            ));
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Reservation not found: " + id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @GetMapping
    @Operation(summary = "List reservations for restaurant")
    public ResponseEntity<Page<ReservationEntity>> listReservations(
            @Parameter(description = "Restaurant UUID") @RequestParam UUID restaurantId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(reservationService.listReservationsByRestaurant(restaurantId, pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel reservation", description = "Cancels reservation within allowed policy window and releases tables. Note: cancellationWindowHours parameter is deprecated in favor of restaurant policy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled"),
            @ApiResponse(responseCode = "409", description = "Cancellation window has passed")
    })
    public ResponseEntity<?> cancelReservation(
            @Parameter(description = "Reservation UUID") @PathVariable UUID id,
            @Parameter(description = "Deprecated: Required advance cancellation hours (ignored, restaurant policy applies)", deprecated = true) @RequestParam(defaultValue = "2") int cancellationWindowHours,
            @Parameter(description = "Reason for cancellation") @RequestParam(required = false) String reason) {
        try {
            ReservationEntity cancelled = reservationService.cancelReservation(id, cancellationWindowHours, reason != null ? reason : "Customer request");
            return ResponseEntity.ok(cancelled);
        } catch (IllegalStateException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/cancellation-window-passed"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
        }
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update reservation lifecycle status", description = "Transitions status: CONFIRMED -> ARRIVED -> COMPLETED, NO_SHOW, or CANCELLED.")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest req) {
        try {
            ReservationEntity updated = reservationService.updateStatus(id, req.status());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    public static class CreateReservationRequest {
        @NotNull(message = "Restaurant ID is required")
        @Schema(description = "Restaurant UUID", requiredMode = Schema.RequiredMode.REQUIRED)
        private final UUID restaurantId;

        @Schema(description = "Customer UUID")
        private final UUID customerId;

        @Size(max = 200, message = "Customer name cannot exceed 200 characters")
        @Schema(description = "Customer display name", example = "Alice Smith", maxLength = 200)
        private final String customerName;

        @Email(message = "Customer email must be a valid email address")
        @Size(max = 255, message = "Customer email cannot exceed 255 characters")
        @Schema(description = "Customer email address", example = "alice@example.com", maxLength = 255)
        private final String customerEmail;

        @Min(value = 1, message = "Party size must be at least 1 guest")
        @Max(value = 50, message = "Party size cannot exceed 50 guests")
        @Schema(description = "Party size (1-50 guests)", example = "4", minimum = "1", maximum = "50", defaultValue = "2")
        private final Integer partySize;

        @Schema(description = "Reservation start timestamp in ISO-8601 format", example = "2026-09-20T19:00:00Z")
        private final Instant startTime;

        @Min(value = 15, message = "Duration must be at least 15 minutes")
        @Max(value = 480, message = "Duration cannot exceed 480 minutes (8 hours)")
        @Schema(description = "Dining duration in minutes (15-480)", example = "90", minimum = "15", maximum = "480", defaultValue = "90")
        private final Integer durationMinutes;

        @Min(value = 0, message = "Cancellation window hours cannot be negative")
        @Max(value = 168, message = "Cancellation window hours cannot exceed 168 hours (7 days)")
        @Schema(description = "Authoritative cancellation window in hours (0-168)", example = "2", minimum = "0", maximum = "168", defaultValue = "2")
        private final Integer cancellationWindowHours;

        @Schema(description = "Available tables passed by caller/engine")
        private final List<TableAllocationEngine.TableCandidate> availableTables;

        @Schema(description = "Available table combinations passed by caller/engine")
        private final List<TableAllocationEngine.CombinationCandidate> combinations;

        @JsonCreator
        public CreateReservationRequest(
                @JsonProperty("restaurantId") UUID restaurantId,
                @JsonProperty("customerId") UUID customerId,
                @JsonProperty("customerName") String customerName,
                @JsonProperty("customerEmail") String customerEmail,
                @JsonProperty("partySize") Integer partySize,
                @JsonProperty("startTime") Instant startTime,
                @JsonProperty("durationMinutes") Integer durationMinutes,
                @JsonProperty("cancellationWindowHours") Integer cancellationWindowHours,
                @JsonProperty("availableTables") List<TableAllocationEngine.TableCandidate> availableTables,
                @JsonProperty("combinations") List<TableAllocationEngine.CombinationCandidate> combinations
        ) {
            this.restaurantId = restaurantId;
            this.customerId = customerId;
            this.customerName = customerName;
            this.customerEmail = customerEmail;
            this.partySize = partySize;
            this.startTime = startTime;
            this.durationMinutes = durationMinutes;
            this.cancellationWindowHours = cancellationWindowHours;
            this.availableTables = availableTables;
            this.combinations = combinations;
        }

        public UUID restaurantId() { return restaurantId; }
        public UUID customerId() { return customerId; }
        public String customerName() { return customerName; }
        public String customerEmail() { return customerEmail; }
        public Integer partySize() { return partySize; }
        public Instant startTime() { return startTime; }
        public Integer durationMinutes() { return durationMinutes; }
        public Integer cancellationWindowHours() { return cancellationWindowHours; }
        public List<TableAllocationEngine.TableCandidate> availableTables() { return availableTables; }
        public List<TableAllocationEngine.CombinationCandidate> combinations() { return combinations; }
    }

    public static class UpdateStatusRequest {
        @NotNull(message = "Status is required")
        @Schema(description = "Lifecycle status transition target", example = "ARRIVED", requiredMode = Schema.RequiredMode.REQUIRED)
        private final ReservationStatus status;

        @JsonCreator
        public UpdateStatusRequest(@JsonProperty("status") ReservationStatus status) {
            this.status = status;
        }

        public ReservationStatus status() { return status; }
    }

    public record ReservationResponseDto(
            UUID id, UUID restaurantId, UUID customerId, String customerName,
            String customerEmail, int partySize, Instant startTime, Instant endTime,
            String status, List<UUID> allocatedTableIds
    ) {}
}
