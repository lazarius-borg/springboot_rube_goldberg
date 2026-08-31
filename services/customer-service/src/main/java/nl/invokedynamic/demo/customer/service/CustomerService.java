package nl.invokedynamic.demo.customer.service;

import nl.invokedynamic.demo.customer.domain.CustomerProfileEntity;
import nl.invokedynamic.demo.customer.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerProfileRepository profileRepository;

    public CustomerService(CustomerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public CustomerProfileEntity getOrCreateProfile(String keycloakSub, String email, String firstName, String lastName) {
        return profileRepository.findByKeycloakSubjectId(keycloakSub)
                .orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    Instant now = Instant.now();
                    CustomerProfileEntity entity = new CustomerProfileEntity(
                            id, keycloakSub, email, firstName, lastName, null, "ACTIVE", now, now
                    );
                    return profileRepository.save(entity);
                });
    }

    @Transactional
    public CustomerProfileEntity updateProfile(String keycloakSub, String firstName, String lastName, String phoneNumber) {
        CustomerProfileEntity entity = profileRepository.findByKeycloakSubjectId(keycloakSub)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for subject: " + keycloakSub));
        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setPhoneNumber(phoneNumber);
        entity.setUpdatedAt(Instant.now());
        return profileRepository.save(entity);
    }
}
