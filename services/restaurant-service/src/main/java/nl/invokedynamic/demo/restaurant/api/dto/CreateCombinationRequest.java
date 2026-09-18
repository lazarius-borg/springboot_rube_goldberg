package nl.invokedynamic.demo.restaurant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateCombinationRequest(
        @Size(max = 100)
        @Schema(description = "Table combination identifier", example = "Party Hall 1", maxLength = 100)
        String name,

        @NotEmpty @Size(min = 2, max = 10)
        @Schema(description = "Distinct table IDs composing the combination (min 2, max 10)")
        List<UUID> tableIds,

        @Min(1) @Max(200)
        @Schema(description = "Combined seating capacity", example = "8", minimum = "1", maximum = "200")
        Integer combinedCapacity
) {
    @AssertTrue(message = "Table combination must contain at least 2 distinct table identifiers")
    public boolean isTableIds() {
        return tableIds != null && tableIds.stream().distinct().count() == tableIds.size();
    }
}
