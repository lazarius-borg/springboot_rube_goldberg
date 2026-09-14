package nl.invokedynamic.demo.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.ReservationCancelledEvent;
import nl.invokedynamic.demo.events.ReservationCreatedEvent;
import nl.invokedynamic.demo.events.ReservationStatusChangedEvent;
import nl.invokedynamic.demo.reservation.domain.*;
import nl.invokedynamic.demo.reservation.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationTableAllocationRepository allocationRepository;
    private final OutboxEventRepository outboxRepository;
    private final TableAllocationEngine allocationEngine;
    private final ObjectMapper objectMapper;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationTableAllocationRepository allocationRepository,
                              OutboxEventRepository outboxRepository,
                              TableAllocationEngine allocationEngine,
                              ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.allocationRepository = allocationRepository;
        this.outboxRepository = outboxRepository;
        this.allocationEngine = allocationEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReservationEntity createReservation(UUID restaurantId, UUID customerId, String customerName,
                                               String customerEmail, int partySize, Instant startTime,
                                               int durationMinutes,
                                               List<TableAllocationEngine.TableCandidate> tableInventory,
                                               List<TableAllocationEngine.CombinationCandidate> combinations) {
        return createReservation(restaurantId, customerId, customerName, customerEmail, partySize, startTime, durationMinutes, 2, tableInventory, combinations);
    }

    @Transactional
    public ReservationEntity createReservation(UUID restaurantId, UUID customerId, String customerName,
                                               String customerEmail, int partySize, Instant startTime,
                                               int durationMinutes,
                                               int cancellationWindowHours,
                                               List<TableAllocationEngine.TableCandidate> tableInventory,
                                               List<TableAllocationEngine.CombinationCandidate> combinations) {
        Instant endTime = startTime.plus(Duration.ofMinutes(durationMinutes > 0 ? durationMinutes : 90));
        Set<UUID> occupied = new HashSet<>(allocationRepository.findOccupiedTableIds(restaurantId, startTime, endTime));

        Optional<List<UUID>> allocatedTables = allocationEngine.allocateTable(partySize, tableInventory, combinations, occupied);
        if (allocatedTables.isEmpty()) {
            throw new IllegalStateException("No suitable tables available for party size " + partySize);
        }

        UUID reservationId = UUID.randomUUID();
        Instant now = Instant.now();
        ReservationEntity reservation = new ReservationEntity(
                reservationId, restaurantId, customerId, customerName, customerEmail,
                partySize, startTime, endTime, "CONFIRMED",
                cancellationWindowHours > 0 ? cancellationWindowHours : 2,
                now, now
        );
        reservationRepository.save(reservation);

        allocationRepository.saveAll(allocatedTables.get().stream()
                .map(tableId -> new ReservationTableAllocationEntity(
                        UUID.randomUUID(), reservationId, tableId, restaurantId, startTime, endTime
                ))
                .toList());

        try {
            ReservationCreatedEvent event = new ReservationCreatedEvent(
                    UUID.randomUUID(), now, reservationId, restaurantId, customerId, customerName,
                    customerEmail, startTime, endTime, partySize, allocatedTables.get()
            );
            outboxRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(), "Reservation", reservationId.toString(), "ReservationCreated",
                    objectMapper.writeValueAsString(event), now
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return reservation;
    }

    public Optional<ReservationEntity> getReservation(UUID id) {
        return reservationRepository.findById(id);
    }

    public Page<ReservationEntity> listReservationsByRestaurant(UUID restaurantId, Pageable pageable) {
        return reservationRepository.findByRestaurantId(restaurantId, pageable);
    }

    public List<UUID> getAllocatedTables(UUID reservationId) {
        return allocationRepository.findByReservationId(reservationId).stream()
                .map(ReservationTableAllocationEntity::getTableId)
                .toList();
    }

    @Transactional
    public ReservationEntity cancelReservation(UUID id, int cancellationWindowHours, String reason) {
        ReservationEntity reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        if ("CANCELLED".equals(reservation.getStatus())) {
            return reservation;
        }

        int effectiveWindow = reservation.getCancellationWindowHours() > 0 ? reservation.getCancellationWindowHours() : 2;
        Instant deadline = reservation.getStartTime().minus(Duration.ofHours(effectiveWindow));
        if (Instant.now().isAfter(deadline)) {
            throw new IllegalStateException("Cancellation deadline has passed (minimum " + effectiveWindow + " hours notice required)");
        }

        List<UUID> releasedTables = getAllocatedTables(id);
        allocationRepository.deleteByReservationId(id);

        reservation.setStatus("CANCELLED");
        reservation.setCancellationReason(reason);
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);

        try {
            ReservationCancelledEvent event = new ReservationCancelledEvent(
                    UUID.randomUUID(), Instant.now(), id, reservation.getRestaurantId(),
                    reservation.getCustomerId(), reservation.getCustomerEmail(), releasedTables,
                    reservation.getStartTime(), reservation.getEndTime(), reservation.getPartySize(), reason
            );
            outboxRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(), "Reservation", id.toString(), "ReservationCancelled",
                    objectMapper.writeValueAsString(event), Instant.now()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return reservation;
    }

    @Transactional
    public ReservationEntity updateStatus(UUID id, String newStatus) {
        return updateStatus(id, ReservationStatus.valueOf(newStatus));
    }

    @Transactional
    public ReservationEntity updateStatus(UUID id, ReservationStatus newStatus) {
        ReservationEntity reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        String previousStatus = reservation.getStatus();
        String targetStatus = newStatus.name();

        // Validate state machine: CONFIRMED -> ARRIVED -> COMPLETED, or CONFIRMED -> NO_SHOW / CANCELLED
        if ("CONFIRMED".equals(previousStatus) && ("ARRIVED".equals(targetStatus) || "NO_SHOW".equals(targetStatus) || "CANCELLED".equals(targetStatus))
                || "ARRIVED".equals(previousStatus) && "COMPLETED".equals(targetStatus)) {
            reservation.setStatus(targetStatus);
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);

            try {
                ReservationStatusChangedEvent event = new ReservationStatusChangedEvent(
                        UUID.randomUUID(), Instant.now(), id, reservation.getRestaurantId(), previousStatus, targetStatus
                );
                outboxRepository.save(new OutboxEventEntity(
                        UUID.randomUUID(), "Reservation", id.toString(), "ReservationStatusChanged",
                        objectMapper.writeValueAsString(event), Instant.now()
                ));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize outbox event", e);
            }

            return reservation;
        }

        throw new IllegalArgumentException("Invalid state transition from " + previousStatus + " to " + targetStatus);
    }
}
