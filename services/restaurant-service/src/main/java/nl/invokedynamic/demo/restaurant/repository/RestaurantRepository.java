package nl.invokedynamic.demo.restaurant.repository;

import nl.invokedynamic.demo.restaurant.domain.RestaurantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<RestaurantEntity, UUID> {}
