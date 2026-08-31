package nl.invokedynamic.demo.customer.repository;

import nl.invokedynamic.demo.customer.domain.CustomerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfileEntity, UUID> {
    Optional<CustomerProfileEntity> findByKeycloakSubjectId(String keycloakSubjectId);
    Optional<CustomerProfileEntity> findByEmail(String email);
}
