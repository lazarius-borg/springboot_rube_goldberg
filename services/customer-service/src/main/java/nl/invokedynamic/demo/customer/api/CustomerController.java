package nl.invokedynamic.demo.customer.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
                                                               @RequestBody UpdateCustomerRequest req) {
        String sub = jwt != null ? jwt.getSubject() : "anonymous-demo";
        return ResponseEntity.ok(customerService.updateProfile(sub, req.firstName(), req.lastName(), req.phoneNumber()));
    }

    public record UpdateCustomerRequest(String firstName, String lastName, String phoneNumber) {}
}
