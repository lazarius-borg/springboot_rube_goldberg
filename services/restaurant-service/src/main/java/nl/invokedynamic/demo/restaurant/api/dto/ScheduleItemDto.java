package nl.invokedynamic.demo.restaurant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleItemDto(
        @Min(1) @Max(7)
        @Schema(description = "Day of week (1 = Monday, 7 = Sunday)", minimum = "1", maximum = "7")
        Integer dayOfWeek,

        @Schema(description = "Specific calendar date for holiday/exception schedules")
        LocalDate specificDate,

        @Schema(description = "Daily opening time", example = "09:00:00")
        LocalTime openTime,

        @Schema(description = "Daily closing time", example = "22:00:00")
        LocalTime closeTime,

        @Schema(description = "Whether the establishment is closed on this schedule day")
        boolean isClosed
) {
    @AssertTrue(message = "Open time and close time are required for open days, and close time must be strictly after open time")
    public boolean isCloseTime() {
        if (isClosed) {
            return true;
        }
        return openTime != null && closeTime != null && closeTime.isAfter(openTime);
    }
}
