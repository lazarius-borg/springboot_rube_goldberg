package nl.invokedynamic.demo.reservation.repository;

import nl.invokedynamic.demo.reservation.domain.ReservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    Page<ReservationEntity> findByRestaurantId(UUID restaurantId, Pageable pageable);
    Page<ReservationEntity> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT r FROM ReservationEntity r WHERE r.restaurantId = :restaurantId AND r.status IN ('CONFIRMED', 'ARRIVED') AND r.startTime < :endTime AND r.endTime > :startTime")
    List<ReservationEntity> findOverlappingActiveReservations(@Param("restaurantId") UUID restaurantId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
}
