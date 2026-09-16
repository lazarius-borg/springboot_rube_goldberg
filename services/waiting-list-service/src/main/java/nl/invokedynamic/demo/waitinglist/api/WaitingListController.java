package nl.invokedynamic.demo.waitinglist.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListEntryEntity;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListOfferEntity;
import nl.invokedynamic.demo.waitinglist.service.WaitingListService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/waiting-list")
@Tag(name = "Waiting List", description = "Fair FIFO waiting list queue and time-limited offer claim operations")
public class WaitingListController {

    private final WaitingListService waitingListService;

    public WaitingListController(WaitingListService waitingListService) {
        this.waitingListService = waitingListService;
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

    public record JoinWaitingListRequest(
            @NotNull
            @Schema(description = "Restaurant UUID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID restaurantId,

            @Schema(description = "Customer UUID")
            UUID customerId,

            @NotBlank @Email @Size(max = 255)
            @Schema(description = "Customer contact email", example = "customer@example.com", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
            String customerEmail,

            @NotNull
            @Schema(description = "Requested dining date (must be current or future)", example = "2026-09-20", requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate targetDate,

            @NotNull
            @Schema(description = "Earliest acceptable seating time", example = "18:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
            LocalTime earliestTime,

            @NotNull
            @Schema(description = "Latest acceptable seating time", example = "21:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
            LocalTime latestTime,

            @Min(1) @Max(50)
            @Schema(description = "Party size between 1 and 50 guests", example = "4", minimum = "1", maximum = "50", requiredMode = Schema.RequiredMode.REQUIRED)
            int partySize
    ) {
        @AssertTrue(message = "Target date must be current or future")
        public boolean isTargetDate() {
            if (targetDate == null) {
                return false;
            }
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Amsterdam"));
            return !targetDate.isBefore(today);
        }

        @AssertTrue(message = "Target date cannot be more than 365 days in advance")
        public boolean isTargetDateWithinHorizon() {
            if (targetDate == null) {
                return false;
            }
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Amsterdam"));
            return !targetDate.isAfter(today.plusDays(365));
        }

        @AssertTrue(message = "Earliest time must be before or equal to latest time")
        public boolean isEarliestTime() {
            return earliestTime != null && latestTime != null && !earliestTime.isAfter(latestTime);
        }
    }
}
