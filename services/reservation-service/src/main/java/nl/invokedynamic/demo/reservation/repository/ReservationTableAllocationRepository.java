package nl.invokedynamic.demo.reservation.repository;

import nl.invokedynamic.demo.reservation.domain.ReservationTableAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationTableAllocationRepository extends JpaRepository<ReservationTableAllocationEntity, UUID> {
    List<ReservationTableAllocationEntity> findByReservationId(UUID reservationId);

    List<ReservationTableAllocationEntity> findByReservationIdIn(Collection<UUID> reservationIds);

    @Query("SELECT r.tableId FROM ReservationTableAllocationEntity r WHERE r.restaurantId = :restaurantId AND r.startTime < :endTime AND r.endTime > :startTime")
    List<UUID> findOccupiedTableIds(@Param("restaurantId") UUID restaurantId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT COUNT(r) FROM ReservationTableAllocationEntity r WHERE r.tableId = :tableId AND r.endTime > :now")
    long countActiveAllocationsForTable(@Param("tableId") UUID tableId, @Param("now") Instant now);

    void deleteByReservationId(UUID reservationId);
}
