package nl.invokedynamic.demo.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.ReservationCancelledEvent;
import nl.invokedynamic.demo.events.ReservationCreatedEvent;
import nl.invokedynamic.demo.events.ReservationStatusChangedEvent;
import nl.invokedynamic.demo.reservation.client.RestaurantClient;
import nl.invokedynamic.demo.reservation.domain.*;
import nl.invokedynamic.demo.reservation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final ReservationTableAllocationRepository allocationRepository;
    private final OutboxEventRepository outboxRepository;
    private final TableAllocationEngine allocationEngine;
    private final ObjectMapper objectMapper;
    private final RestaurantClient restaurantClient;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationTableAllocationRepository allocationRepository,
                              OutboxEventRepository outboxRepository,
                              TableAllocationEngine allocationEngine,
                              ObjectMapper objectMapper,
                              RestaurantClient restaurantClient) {
        this.reservationRepository = reservationRepository;
        this.allocationRepository = allocationRepository;
        this.outboxRepository = outboxRepository;
        this.allocationEngine = allocationEngine;
        this.objectMapper = objectMapper;
        this.restaurantClient = restaurantClient != null ? restaurantClient : new DefaultRestaurantClient();
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
        if (partySize <= 0) {
            throw new IllegalArgumentException("Party size must be greater than 0");
        }

        // US4: Check operating schedule closed days
        if (restaurantClient.isClosedAt(restaurantId, startTime)) {
            throw new IllegalStateException("Restaurant is closed on this schedule date");
        }

        // US3: Duration validation
        int effectiveDuration = durationMinutes > 0 ? durationMinutes : 90;
        if (effectiveDuration < 15) {
            throw new IllegalArgumentException("Reservation duration must be at least 15 minutes");
        }
        int minDuration = restaurantClient.getMinReservationDurationMinutes(restaurantId);
        if (minDuration > 0 && effectiveDuration < minDuration) {
            throw new IllegalArgumentException("Requested duration of " + effectiveDuration + " minutes is less than restaurant minimum duration of " + minDuration + " minutes");
        }
        int maxDuration = restaurantClient.getMaxReservationDurationMinutes(restaurantId);
        if (maxDuration > 0 && effectiveDuration > maxDuration) {
            throw new IllegalArgumentException("Requested duration of " + effectiveDuration + " minutes exceeds restaurant maximum duration of " + maxDuration + " minutes");
        }

        Instant endTime = startTime.plus(Duration.ofMinutes(effectiveDuration));

        if (!restaurantClient.isWithinOperatingHours(restaurantId, startTime, endTime)) {
            throw new IllegalArgumentException("Reservation timeslot is outside restaurant operating hours or extends past closing time");
        }

        // Resolve real inventory if not supplied
        List<TableAllocationEngine.TableCandidate> effectiveTables = (tableInventory != null && !tableInventory.isEmpty())
                ? tableInventory
                : restaurantClient.getTableCandidates(restaurantId);

        List<TableAllocationEngine.CombinationCandidate> effectiveCombinations = (combinations != null && !combinations.isEmpty())
                ? combinations
                : restaurantClient.getCombinationCandidates(restaurantId);

        // US2: Total restaurant physical capacity check across overlapping timeslot
        int totalCapacity = effectiveTables.stream().mapToInt(TableAllocationEngine.TableCandidate::capacity).sum();
        if (totalCapacity == 0) {
            totalCapacity = restaurantClient.getTotalCapacity(restaurantId);
        }

        if (totalCapacity > 0) {
            if (partySize > totalCapacity) {
                log.warn("Party size {} exceeds total physical capacity {} of restaurant {}", partySize, totalCapacity, restaurantId);
                throw new IllegalStateException("Party size " + partySize + " exceeds total restaurant physical capacity of " + totalCapacity);
            }
            List<ReservationEntity> overlapping = reservationRepository.findOverlappingActiveReservations(restaurantId, startTime, endTime);
            int currentOccupiedGuests = overlapping.stream().mapToInt(ReservationEntity::getPartySize).sum();
            if (currentOccupiedGuests + partySize > totalCapacity) {
                log.warn("Timeslot capacity exceeded for restaurant {}: {} currently reserved + {} requested > {}",
                        restaurantId, currentOccupiedGuests, partySize, totalCapacity);
                throw new IllegalStateException("Timeslot capacity exceeded: current overlapping reservations (" + currentOccupiedGuests + ") + requested party (" + partySize + ") exceeds total capacity (" + totalCapacity + ")");
            }
        }

        Set<UUID> occupied = new HashSet<>(allocationRepository.findOccupiedTableIds(restaurantId, startTime, endTime));

        Optional<List<UUID>> allocatedTables = allocationEngine.allocateTable(partySize, effectiveTables, effectiveCombinations, occupied);
        if (allocatedTables.isEmpty()) {
            log.warn("Table allocation failed for restaurant {} with party size {}", restaurantId, partySize);
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
            log.error("Failed to serialize outbox event for reservation {}", reservationId, e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        log.info("Created reservation {} for restaurant {} customer {} party size {} allocated tables {}",
                reservationId, restaurantId, customerId, partySize, allocatedTables.get());
        return reservation;
    }

    public Optional<ReservationEntity> getReservation(UUID id) {
        return reservationRepository.findById(id);
    }

    public Page<ReservationEntity> listReservationsByRestaurant(UUID restaurantId, Pageable pageable) {
        return reservationRepository.findByRestaurantId(restaurantId, pageable);
    }

    public Page<ReservationEntity> listReservationsByCustomer(UUID customerId, Pageable pageable) {
        return reservationRepository.findByCustomerId(customerId, pageable);
    }

    public List<UUID> getAllocatedTables(UUID reservationId) {
        return allocationRepository.findByReservationId(reservationId).stream()
                .map(ReservationTableAllocationEntity::getTableId)
                .toList();
    }

    public Map<UUID, List<UUID>> getAllocatedTablesBatch(Collection<UUID> reservationIds) {
        if (reservationIds == null || reservationIds.isEmpty()) return Map.of();
        List<ReservationTableAllocationEntity> allocations = allocationRepository.findByReservationIdIn(reservationIds);
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (UUID resId : reservationIds) {
            map.put(resId, new ArrayList<>());
        }
        for (ReservationTableAllocationEntity a : allocations) {
            map.computeIfAbsent(a.getReservationId(), k -> new ArrayList<>()).add(a.getTableId());
        }
        return map;
    }

    public Map<UUID, String> getTableLabels(UUID restaurantId) {
        return restaurantClient.getTableLabels(restaurantId);
    }

    public boolean hasActiveReservationsForTable(UUID tableId) {
        return allocationRepository.countActiveAllocationsForTable(tableId, Instant.now()) > 0;
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
            log.error("Failed to serialize outbox event for cancelled reservation {}", id, e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        log.info("Cancelled reservation {} for restaurant {} with reason: {}", id, reservation.getRestaurantId(), reason);
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

        if ("CONFIRMED".equals(previousStatus) && ("ARRIVED".equals(targetStatus) || "NO_SHOW".equals(targetStatus) || "CANCELLED".equals(targetStatus))
                || "ARRIVED".equals(previousStatus) && "COMPLETED".equals(targetStatus)) {
            reservation.setStatus(targetStatus);
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);

            try {
                ReservationStatusChangedEvent event = new ReservationStatusChangedEvent(
                        UUID.randomUUID(), Instant.now(), id, reservation.getRestaurantId(),
                        previousStatus, targetStatus
                );
                outboxRepository.save(new OutboxEventEntity(
                        UUID.randomUUID(), "Reservation", id.toString(), "ReservationStatusChanged",
                        objectMapper.writeValueAsString(event), Instant.now()
                ));
            } catch (Exception e) {
                log.error("Failed to serialize outbox event for status change on reservation {}", id, e);
                throw new RuntimeException("Failed to serialize outbox event", e);
            }

            log.info("Transitioned reservation {} from {} to {}", id, previousStatus, targetStatus);
            return reservation;
        }

        throw new IllegalArgumentException("Invalid state transition from " + previousStatus + " to " + targetStatus);
    }

    private static class DefaultRestaurantClient implements RestaurantClient {
        @Override
        public List<TableAllocationEngine.TableCandidate> getTableCandidates(UUID restaurantId) {
            return List.of();
        }

        @Override
        public Map<UUID, String> getTableLabels(UUID restaurantId) {
            return Map.of();
        }

        @Override
        public List<TableAllocationEngine.CombinationCandidate> getCombinationCandidates(UUID restaurantId) {
            return List.of();
        }

        @Override
        public int getMinReservationDurationMinutes(UUID restaurantId) {
            return 45;
        }

        @Override
        public int getMaxReservationDurationMinutes(UUID restaurantId) {
            return 180;
        }

        @Override
        public int getTotalCapacity(UUID restaurantId) {
            return 0;
        }

        @Override
        public boolean isClosedAt(UUID restaurantId, Instant time) {
            return false;
        }
    }
}
