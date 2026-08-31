package nl.invokedynamic.demo.customer.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerProfileTest {

    @Test
    void shouldCreateCustomerProfile() {
        CustomerProfileEntity entity = new CustomerProfileEntity(
                UUID.randomUUID(), "sub-123", "alice@example.com", "Alice", "Customer", "+31612345678", "ACTIVE", Instant.now(), Instant.now()
        );

        assertThat(entity.getEmail()).isEqualTo("alice@example.com");
        assertThat(entity.getFirstName()).isEqualTo("Alice");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }
}
