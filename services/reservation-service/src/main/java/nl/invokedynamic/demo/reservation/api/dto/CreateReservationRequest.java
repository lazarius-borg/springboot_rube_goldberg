package nl.invokedynamic.demo.reservation.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine.CombinationCandidate;
import nl.invokedynamic.demo.reservation.domain.TableAllocationEngine.TableCandidate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CreateReservationRequest {
    @NotNull(message = "Restaurant ID is required")
    @Schema(description = "Restaurant UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID restaurantId;

    @Schema(description = "Customer UUID")
    private final UUID customerId;

    @Size(max = 200, message = "Customer name cannot exceed 200 characters")
    @Schema(description = "Customer display name", example = "Alice Smith", maxLength = 200)
    private final String customerName;

    @Email(message = "Customer email must be a valid email address")
    @Size(max = 255, message = "Customer email cannot exceed 255 characters")
    @Schema(description = "Customer email address", example = "alice@example.com", maxLength = 255)
    private final String customerEmail;

    @Min(value = 1, message = "Party size must be at least 1 guest")
    @Max(value = 50, message = "Party size cannot exceed 50 guests")
    @Schema(description = "Party size (1-50 guests)", example = "4", minimum = "1", maximum = "50", defaultValue = "2")
    private final Integer partySize;

    @Schema(description = "Reservation start timestamp in ISO-8601 format", example = "2026-09-20T19:00:00Z")
    private final Instant startTime;

    @Min(value = 15, message = "Duration must be at least 15 minutes")
    @Max(value = 480, message = "Duration cannot exceed 480 minutes (8 hours)")
    @Schema(description = "Dining duration in minutes (15-480)", example = "90", minimum = "15", maximum = "480", defaultValue = "90")
    private final Integer durationMinutes;

    @Min(value = 0, message = "Cancellation window hours cannot be negative")
    @Max(value = 168, message = "Cancellation window hours cannot exceed 168 hours (7 days)")
    @Schema(description = "Authoritative cancellation window in hours (0-168)", example = "2", minimum = "0", maximum = "168", defaultValue = "2")
    private final Integer cancellationWindowHours;

    @Schema(description = "Available tables passed by caller/engine")
    private final List<TableCandidate> availableTables;

    @Schema(description = "Available table combinations passed by caller/engine")
    private final List<CombinationCandidate> combinations;

    @JsonCreator
    public CreateReservationRequest(
            @JsonProperty("restaurantId") UUID restaurantId,
            @JsonProperty("customerId") UUID customerId,
            @JsonProperty("customerName") String customerName,
            @JsonProperty("customerEmail") String customerEmail,
            @JsonProperty("partySize") Integer partySize,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("durationMinutes") Integer durationMinutes,
            @JsonProperty("cancellationWindowHours") Integer cancellationWindowHours,
            @JsonProperty("availableTables") List<TableCandidate> availableTables,
            @JsonProperty("combinations") List<CombinationCandidate> combinations
    ) {
        this.restaurantId = restaurantId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.partySize = partySize;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.cancellationWindowHours = cancellationWindowHours;
        this.availableTables = availableTables;
        this.combinations = combinations;
    }

    public UUID restaurantId() { return restaurantId; }
    public UUID customerId() { return customerId; }
    public String customerName() { return customerName; }
    public String customerEmail() { return customerEmail; }
    public Integer partySize() { return partySize; }
    public Instant startTime() { return startTime; }
    public Integer durationMinutes() { return durationMinutes; }
    public Integer cancellationWindowHours() { return cancellationWindowHours; }
    public List<TableCandidate> availableTables() { return availableTables; }
    public List<CombinationCandidate> combinations() { return combinations; }
}
