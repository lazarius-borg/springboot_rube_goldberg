package nl.invokedynamic.demo.restaurant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OpeningHoursConfigDto(
        @NotEmpty
        @Schema(description = "List of opening schedule items")
        List<@Valid ScheduleItemDto> schedules
) {}
