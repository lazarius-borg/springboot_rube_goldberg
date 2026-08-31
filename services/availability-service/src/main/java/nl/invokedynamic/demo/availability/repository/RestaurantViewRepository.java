package nl.invokedynamic.demo.availability.repository;

import nl.invokedynamic.demo.availability.domain.RestaurantViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RestaurantViewRepository extends JpaRepository<RestaurantViewEntity, UUID> {}
