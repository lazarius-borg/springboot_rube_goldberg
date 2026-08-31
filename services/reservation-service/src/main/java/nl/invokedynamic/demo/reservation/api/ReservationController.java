package nl.invokedynamic.demo.reservation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine;
import nl.invokedynamic.demo.reservation.service.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public ResponseEntity<?> createReservation(@RequestBody CreateReservationRequest req) {
        try {
            List<TableAllocationEngine.TableCandidate> tables = req.availableTables() != null ? req.availableTables() :
                    List.of(new TableAllocationEngine.TableCandidate(UUID.randomUUID(), req.partySize()));
            List<TableAllocationEngine.CombinationCandidate> combinations = req.combinations() != null ? req.combinations() : List.of();

            ReservationEntity reservation = reservationService.createReservation(
                    req.restaurantId(),
                    req.customerId() != null ? req.customerId() : UUID.randomUUID(),
                    req.customerName() != null ? req.customerName() : "Customer",
                    req.customerEmail() != null ? req.customerEmail() : "customer@example.com",
                    req.partySize(),
                    req.startTime(),
                    req.durationMinutes() > 0 ? req.durationMinutes() : 90,
                    tables,
                    combinations
            );

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
        } catch (Exception e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
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
            @Parameter(description = "Restaurant UUID") @RequestParam UUID restaurantId, Pageable pageable) {
        return ResponseEntity.ok(reservationService.listReservationsByRestaurant(restaurantId, pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel reservation", description = "Cancels reservation within allowed policy window and releases tables.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled"),
            @ApiResponse(responseCode = "409", description = "Cancellation window has passed")
    })
    public ResponseEntity<?> cancelReservation(
            @Parameter(description = "Reservation UUID") @PathVariable UUID id,
            @Parameter(description = "Required advance cancellation hours") @RequestParam(defaultValue = "2") int cancellationWindowHours,
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
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest req) {
        try {
            ReservationEntity updated = reservationService.updateStatus(id, req.status());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    public record CreateReservationRequest(UUID restaurantId, UUID customerId, String customerName, String customerEmail,
                                          int partySize, Instant startTime, int durationMinutes,
                                          List<TableAllocationEngine.TableCandidate> availableTables,
                                          List<TableAllocationEngine.CombinationCandidate> combinations) {}

    public record UpdateStatusRequest(String status) {}

    public record ReservationResponseDto(UUID id, UUID restaurantId, UUID customerId, String customerName,
                                         String customerEmail, int partySize, Instant startTime, Instant endTime,
                                         String status, List<UUID> allocatedTableIds) {}
}
