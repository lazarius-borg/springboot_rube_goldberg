package nl.invokedynamic.demo.restaurant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRestaurantRequest(
        @NotBlank @Size(max = 150)
        @Schema(description = "Restaurant trade name", example = "The Bistro", maxLength = 150)
        String name,

        @NotBlank @Size(max = 1000)
        @Schema(description = "Physical address", example = "123 Main St, Amsterdam", maxLength = 1000)
        String address,

        @NotBlank @Size(max = 50)
        @Schema(description = "IANA Timezone identifier", example = "Europe/Amsterdam", maxLength = 50)
        String timezone,

        @Min(15) @Max(480)
        @Schema(description = "Minimum reservation duration in minutes (15-480)", example = "45", minimum = "15", maximum = "480", defaultValue = "45")
        Integer minReservationDurationMinutes,

        @Min(15) @Max(480)
        @Schema(description = "Default reservation duration in minutes (15-480)", example = "90", minimum = "15", maximum = "480", defaultValue = "90")
        Integer defaultReservationDurationMinutes,

        @Min(15) @Max(480)
        @Schema(description = "Maximum reservation duration in minutes (15-480)", example = "180", minimum = "15", maximum = "480", defaultValue = "180")
        Integer maxReservationDurationMinutes,

        @Min(0) @Max(10080)
        @Schema(description = "Minimum booking advance lead time in minutes (0-10080)", example = "30", minimum = "0", maximum = "10080", defaultValue = "30")
        Integer minBookingAdvanceMinutes,

        @Min(1) @Max(365)
        @Schema(description = "Maximum forward booking horizon in days (1-365)", example = "60", minimum = "1", maximum = "365", defaultValue = "60")
        Integer maxBookingHorizonDays,

        @Min(0) @Max(168)
        @Schema(description = "Authoritative cancellation window notice in hours (0-168)", example = "2", minimum = "0", maximum = "168", defaultValue = "2")
        Integer cancellationWindowHours
) {
    public CreateRestaurantRequest(String name, String address, String timezone,
                                   Integer defaultReservationDurationMinutes,
                                   Integer maxReservationDurationMinutes,
                                   Integer minBookingAdvanceMinutes,
                                   Integer maxBookingHorizonDays,
                                   Integer cancellationWindowHours) {
        this(name, address, timezone, 45, defaultReservationDurationMinutes, maxReservationDurationMinutes,
                minBookingAdvanceMinutes, maxBookingHorizonDays, cancellationWindowHours);
    }
}
