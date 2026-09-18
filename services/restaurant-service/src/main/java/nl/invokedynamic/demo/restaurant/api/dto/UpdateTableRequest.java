package nl.invokedynamic.demo.restaurant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTableRequest(
        @NotBlank @Size(max = 50)
        @Schema(description = "Table identifier or number", example = "T1", maxLength = 50)
        String tableNumber,

        @NotNull @Min(1) @Max(50)
        @Schema(description = "Physical seating capacity (1-50)", example = "4", minimum = "1", maximum = "50")
        Integer capacity,

        @Size(max = 50)
        @Schema(description = "Floor zone / location", example = "Rooftop", maxLength = 50)
        String zone
) {}
