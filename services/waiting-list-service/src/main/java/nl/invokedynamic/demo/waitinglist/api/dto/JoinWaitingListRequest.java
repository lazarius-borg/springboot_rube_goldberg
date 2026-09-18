package nl.invokedynamic.demo.waitinglist.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

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
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Amsterdam"));
        return !targetDate.isBefore(today);
    }

    @AssertTrue(message = "Target date cannot be more than 365 days in advance")
    public boolean isTargetDateWithinHorizon() {
        if (targetDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Amsterdam"));
        return !targetDate.isAfter(today.plusDays(365));
    }

    @AssertTrue(message = "Earliest time must be before or equal to latest time")
    public boolean isEarliestTime() {
        return earliestTime != null && latestTime != null && !earliestTime.isAfter(latestTime);
    }
}
