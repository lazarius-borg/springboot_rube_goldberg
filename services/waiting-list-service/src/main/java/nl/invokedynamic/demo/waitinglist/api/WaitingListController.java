package nl.invokedynamic.demo.waitinglist.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import nl.invokedynamic.demo.waitinglist.api.dto.JoinWaitingListRequest;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListEntryEntity;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListOfferEntity;
import nl.invokedynamic.demo.waitinglist.service.WaitingListService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/waiting-list")
@Tag(name = "Waiting List", description = "Fair FIFO waiting list queue and time-limited offer claim operations")
public class WaitingListController {

    private final WaitingListService waitingListService;

    public WaitingListController(WaitingListService waitingListService) {
        this.waitingListService = waitingListService;
    }

    @GetMapping
    @Operation(summary = "Get waiting list entries", description = "Inspection of fair FIFO queue for a restaurant or customer.")
    @ApiResponse(responseCode = "200", description = "Waiting list entries retrieved")
    public ResponseEntity<?> getWaitingList(
            @Parameter(description = "Restaurant UUID") @RequestParam(required = false) UUID restaurantId,
            @Parameter(description = "Customer UUID") @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Target dining date") @RequestParam(required = false) LocalDate targetDate,
            @Parameter(description = "Entry status filter") @RequestParam(required = false) String status) {
        if (customerId != null) {
            return ResponseEntity.ok(waitingListService.getWaitingListByCustomer(customerId));
        }
        if (restaurantId != null) {
            List<WaitingListEntryEntity> entries = waitingListService.getWaitingList(restaurantId, targetDate, status);
            return ResponseEntity.ok(entries);
        }
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Either restaurantId or customerId must be provided"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Leave waiting list", description = "Cancels active waiting list placement.")
    public ResponseEntity<?> leaveWaitingList(@Parameter(description = "Waiting list entry UUID") @PathVariable UUID id) {
        try {
            WaitingListEntryEntity cancelled = waitingListService.cancelWaitingListEntry(id);
            return ResponseEntity.ok(cancelled);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
        }
    }

    @PostMapping
    @Operation(summary = "Join FIFO waiting list", description = "Places customer in fair chronological queue for cancellation openings.")
    @ApiResponse(responseCode = "201", description = "Placed on waiting list")
    public ResponseEntity<WaitingListEntryEntity> joinWaitingList(@Valid @RequestBody JoinWaitingListRequest req) {
        WaitingListEntryEntity entry = waitingListService.joinWaitingList(
                req.restaurantId(),
                req.customerId() != null ? req.customerId() : UUID.randomUUID(),
                req.customerEmail() != null ? req.customerEmail() : "customer@example.com",
                req.targetDate(), req.earliestTime(), req.latestTime(), req.partySize()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @PostMapping("/offers/{offerId}/accept")
    @Operation(summary = "Accept time-limited offer", description = "Claims an offered cancellation opening before expiration deadline.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer accepted successfully"),
            @ApiResponse(responseCode = "410", description = "Offer has expired or already converted")
    })
    public ResponseEntity<?> acceptOffer(@Parameter(description = "Offer UUID") @PathVariable UUID offerId) {
        try {
            WaitingListOfferEntity offer = waitingListService.acceptOffer(offerId);
            return ResponseEntity.ok(offer);
        } catch (IllegalStateException e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
            pd.setType(URI.create("https://example.invalid/problems/offer-expired"));
            return ResponseEntity.status(HttpStatus.GONE).body(pd);
        }
    }
}
