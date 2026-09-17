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
import nl.invokedynamic.demo.reservation.api.dto.CreateReservationRequest;
import nl.invokedynamic.demo.reservation.api.dto.ReservationResponseDto;
import nl.invokedynamic.demo.reservation.api.dto.UpdateStatusRequest;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Guaranteed reservation creation, table allocation, lifecycle, and cancellation")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
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

            Instant now = Instant.now();
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

            List<TableAllocationEngine.TableCandidate> tables = req.availableTables();
            List<TableAllocationEngine.CombinationCandidate> combinations = req.combinations();

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
            Map<UUID, String> labelMap = reservationService.getTableLabels(reservation.getRestaurantId());
            List<String> labels = allocated.stream()
                    .map(tid -> labelMap.getOrDefault(tid, "T-" + tid.toString().substring(0, 4)))
                    .toList();

            ReservationResponseDto response = new ReservationResponseDto(
                    reservation.getId(), reservation.getRestaurantId(), reservation.getCustomerId(),
                    reservation.getCustomerName(), reservation.getCustomerEmail(), reservation.getPartySize(),
                    reservation.getStartTime(), reservation.getEndTime(), reservation.getStatus(), allocated,
                    labels, reservation.getCancellationReason()
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
            Map<UUID, String> labelMap = reservationService.getTableLabels(res.getRestaurantId());
            List<String> labels = allocated.stream()
                    .map(tid -> labelMap.getOrDefault(tid, "T-" + tid.toString().substring(0, 4)))
                    .toList();
            return ResponseEntity.ok(new ReservationResponseDto(
                    res.getId(), res.getRestaurantId(), res.getCustomerId(), res.getCustomerName(),
                    res.getCustomerEmail(), res.getPartySize(), res.getStartTime(), res.getEndTime(),
                    res.getStatus(), allocated, labels, res.getCancellationReason()
            ));
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Reservation not found: " + id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @GetMapping
    @Operation(summary = "List reservations for restaurant or customer")
    public ResponseEntity<?> listReservations(
            @Parameter(description = "Restaurant UUID") @RequestParam(required = false) UUID restaurantId,
            @Parameter(description = "Customer UUID") @RequestParam(required = false) UUID customerId,
            @ParameterObject Pageable pageable) {
        if (customerId != null) {
            Page<ReservationEntity> page = reservationService.listReservationsByCustomer(customerId, pageable);
            return ResponseEntity.ok(enrichReservationsPage(page));
        }
        if (restaurantId != null) {
            Page<ReservationEntity> page = reservationService.listReservationsByRestaurant(restaurantId, pageable);
            return ResponseEntity.ok(enrichReservationsPage(page));
        }
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Either restaurantId or customerId must be provided"));
    }

    @GetMapping("/tables/{tableId}/has-active")
    @Operation(summary = "Check if table has active upcoming reservations")
    public ResponseEntity<Boolean> hasActiveReservations(@PathVariable UUID tableId) {
        return ResponseEntity.ok(reservationService.hasActiveReservationsForTable(tableId));
    }

    private Page<ReservationResponseDto> enrichReservationsPage(Page<ReservationEntity> page) {
        List<ReservationEntity> content = page.getContent();
        if (content.isEmpty()) {
            return page.map(r -> null);
        }
        List<UUID> resIds = content.stream().map(ReservationEntity::getId).toList();
        Map<UUID, List<UUID>> allocationMap = reservationService.getAllocatedTablesBatch(resIds);

        // Batch fetch table labels per restaurant to avoid N+1 queries
        Set<UUID> restIds = content.stream().map(ReservationEntity::getRestaurantId).collect(Collectors.toSet());
        Map<UUID, Map<UUID, String>> restTableLabels = new HashMap<>();
        for (UUID rid : restIds) {
            restTableLabels.put(rid, reservationService.getTableLabels(rid));
        }

        return page.map(r -> {
            List<UUID> tables = allocationMap.getOrDefault(r.getId(), List.of());
            Map<UUID, String> labelMap = restTableLabels.getOrDefault(r.getRestaurantId(), Map.of());
            List<String> labels = tables.stream()
                    .map(tid -> labelMap.getOrDefault(tid, "T-" + tid.toString().substring(0, 4)))
                    .toList();
            return new ReservationResponseDto(
                    r.getId(), r.getRestaurantId(), r.getCustomerId(),
                    r.getCustomerName(), r.getCustomerEmail(), r.getPartySize(),
                    r.getStartTime(), r.getEndTime(), r.getStatus(), tables, labels, r.getCancellationReason()
            );
        });
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
}
