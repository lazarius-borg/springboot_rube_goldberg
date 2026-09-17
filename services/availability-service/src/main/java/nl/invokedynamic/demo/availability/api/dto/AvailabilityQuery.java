package nl.invokedynamic.demo.availability.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityQuery(
        @NotNull
        @Parameter(description = "Restaurant UUID", required = true)
        UUID restaurantId,

        @NotNull
        @Parameter(description = "Date of dining (YYYY-MM-DD)", required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @NotNull
        @Parameter(description = "Time of dining (HH:mm:ss)", required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime time,

        @Min(1) @Max(50)
        @Parameter(description = "Number of guests in party (1-50)")
        Integer partySize
) {}
