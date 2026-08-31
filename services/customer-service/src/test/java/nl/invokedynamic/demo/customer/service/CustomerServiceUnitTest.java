package nl.invokedynamic.demo.customer.service;

import nl.invokedynamic.demo.customer.domain.CustomerProfileEntity;
import nl.invokedynamic.demo.customer.repository.CustomerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceUnitTest {

    @Mock private CustomerProfileRepository profileRepository;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(profileRepository);
    }

    @Test
    void shouldCreateNewProfileIfNotExists() {
        when(profileRepository.findByKeycloakSubjectId("sub-1")).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CustomerProfileEntity profile = service.getOrCreateProfile("sub-1", "alice@example.com", "Alice", "Customer");
        assertThat(profile.getEmail()).isEqualTo("alice@example.com");
        assertThat(profile.getFirstName()).isEqualTo("Alice");
    }

    @Test
    void shouldReturnExistingProfileIfExists() {
        CustomerProfileEntity existing = new CustomerProfileEntity(
                UUID.randomUUID(), "sub-1", "alice@example.com", "Alice", "Customer", "+31000", "ACTIVE", Instant.now(), Instant.now()
        );
        when(profileRepository.findByKeycloakSubjectId("sub-1")).thenReturn(Optional.of(existing));

        CustomerProfileEntity profile = service.getOrCreateProfile("sub-1", "alice@example.com", "Alice", "Customer");
        assertThat(profile).isSameAs(existing);
        verify(profileRepository, never()).save(any());
    }
}
