package nl.invokedynamic.demo.reservation.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import nl.invokedynamic.demo.reservation.domain.ReservationStatus;

public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    @Schema(description = "Lifecycle status transition target", example = "ARRIVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final ReservationStatus status;

    @JsonCreator
    public UpdateStatusRequest(@JsonProperty("status") ReservationStatus status) {
        this.status = status;
    }

    public ReservationStatus status() { return status; }
}
