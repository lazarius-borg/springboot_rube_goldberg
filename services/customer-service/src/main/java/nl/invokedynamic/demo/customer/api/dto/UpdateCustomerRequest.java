package nl.invokedynamic.demo.customer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @NotBlank @Size(max = 50)
        @Schema(description = "Customer first name", example = "Alice", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
        String firstName,

        @NotBlank @Size(max = 50)
        @Schema(description = "Customer last name", example = "Smith", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
        String lastName,

        @NotBlank @Size(min = 5, max = 25)
        @Pattern(regexp = "^[+0-9() -]+$", message = "Phone number must contain only numbers, +, -, (), and spaces")
        @Schema(description = "Contact phone number (5 to 25 characters)", example = "+31612345678", minLength = 5, maxLength = 25, requiredMode = Schema.RequiredMode.REQUIRED)
        String phoneNumber
) {}
