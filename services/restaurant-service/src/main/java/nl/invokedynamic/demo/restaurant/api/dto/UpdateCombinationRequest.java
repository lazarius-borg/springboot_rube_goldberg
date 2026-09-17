package nl.invokedynamic.demo.restaurant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public record UpdateCombinationRequest(
        @Schema(description = "Updated custom name for the table combination", example = "Window Booth Combo")
        String name,

        @Positive(message = "Combined capacity must be greater than 0")
        @Schema(description = "Updated seating capacity, cannot exceed sum of member table capacities", example = "8")
        Integer combinedCapacity
) {}
