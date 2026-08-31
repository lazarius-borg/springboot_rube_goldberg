package nl.invokedynamic.demo.reservation.repository;

import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    Page<ReservationEntity> findByRestaurantId(UUID restaurantId, Pageable pageable);
    Page<ReservationEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
