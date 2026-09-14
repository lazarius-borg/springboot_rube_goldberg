package nl.invokedynamic.demo.customer.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nl.invokedynamic.demo.customer.domain.CustomerProfileEntity;
import nl.invokedynamic.demo.customer.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer Profile", description = "Authenticated customer profile retrieval and updates")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated customer profile")
    @ApiResponse(responseCode = "200", description = "Customer profile retrieved or created")
    public ResponseEntity<CustomerProfileEntity> getCurrentCustomer(@AuthenticationPrincipal Jwt jwt) {
        String sub = jwt != null ? jwt.getSubject() : "anonymous-demo";
        String email = jwt != null ? jwt.getClaimAsString("email") : "customer1@example.com";
        String first = jwt != null ? jwt.getClaimAsString("given_name") : "Alice";
        String last = jwt != null ? jwt.getClaimAsString("family_name") : "Customer";

        return ResponseEntity.ok(customerService.getOrCreateProfile(sub, email != null ? email : "customer@example.com", first != null ? first : "Alice", last != null ? last : "Customer"));
    }

    @PutMapping("/me")
    @Operation(summary = "Update customer contact details")
    @ApiResponse(responseCode = "200", description = "Customer profile updated")
    public ResponseEntity<CustomerProfileEntity> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody UpdateCustomerRequest req) {
        String sub = jwt != null ? jwt.getSubject() : "anonymous-demo";
        return ResponseEntity.ok(customerService.updateProfile(sub, req.firstName(), req.lastName(), req.phoneNumber()));
    }

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
}
