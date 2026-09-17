package nl.invokedynamic.demo.waitinglist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.WaitingListEntryCreatedEvent;
import nl.invokedynamic.demo.events.WaitingListOfferAcceptedEvent;
import nl.invokedynamic.demo.events.WaitingListOfferCreatedEvent;
import nl.invokedynamic.demo.waitinglist.client.TableInventoryClient;
import nl.invokedynamic.demo.waitinglist.domain.*;
import nl.invokedynamic.demo.waitinglist.repository.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class WaitingListService {

    private final WaitingListEntryRepository entryRepository;
    private final WaitingListOfferRepository offerRepository;
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TableInventoryClient tableInventoryClient;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Amsterdam");

    public WaitingListService(WaitingListEntryRepository entryRepository,
                              WaitingListOfferRepository offerRepository,
                              OutboxEventRepository outboxRepository,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              ObjectMapper objectMapper,
                              TableInventoryClient tableInventoryClient) {
        this.entryRepository = entryRepository;
        this.offerRepository = offerRepository;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tableInventoryClient = tableInventoryClient;
    }

    @Transactional
    public WaitingListEntryEntity joinWaitingList(UUID restaurantId, UUID customerId, String customerEmail,
                                                  LocalDate targetDate, LocalTime earliestTime, LocalTime latestTime,
                                                  int partySize) {
        ZonedDateTime nowInZone = ZonedDateTime.now(DEFAULT_ZONE);
        LocalDate today = nowInZone.toLocalDate();

        if (targetDate.isBefore(today)) {
            throw new IllegalArgumentException("Target date must not be in the past");
        }
        if (targetDate.isAfter(today.plusDays(365))) {
            throw new IllegalArgumentException("Target date cannot be more than 365 days in advance");
        }

        LocalTime effectiveEarliest = earliestTime;
        if (targetDate.isEqual(today)) {
            LocalTime nowTime = nowInZone.toLocalTime();
            if (latestTime.isBefore(nowTime)) {
                throw new IllegalArgumentException("Seating time window has already passed");
            }
            if (earliestTime.isBefore(nowTime.minusMinutes(5))) {
                throw new IllegalArgumentException("Earliest seating time cannot be in the past");
            }
            if (earliestTime.isBefore(nowTime)) {
                effectiveEarliest = nowTime;
            }
        }

        UUID entryId = UUID.randomUUID();
        Instant now = Instant.now();
        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                entryId, restaurantId, customerId, customerEmail, targetDate, effectiveEarliest, latestTime, partySize, "WAITING", now
        );
        entryRepository.save(entry);

        try {
            WaitingListEntryCreatedEvent event = new WaitingListEntryCreatedEvent(
                    UUID.randomUUID(), now, entryId, restaurantId, customerId, customerEmail, targetDate, effectiveEarliest, latestTime, partySize
            );
            outboxRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(), "WaitingListEntry", entryId.toString(), "WaitingListEntryCreated",
                    objectMapper.writeValueAsString(event), now
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return entry;
    }

    @Transactional(readOnly = true)
    public List<WaitingListEntryEntity> getWaitingList(UUID restaurantId, LocalDate targetDate, String status) {
        if (targetDate != null && status != null && !status.isBlank()) {
            return entryRepository.findByRestaurantIdAndTargetDateAndStatusOrderByCreatedAtAsc(restaurantId, targetDate, status);
        } else if (targetDate != null) {
            return entryRepository.findByRestaurantIdAndTargetDateOrderByCreatedAtAsc(restaurantId, targetDate);
        } else if (status != null && !status.isBlank()) {
            return entryRepository.findByRestaurantIdAndStatusOrderByCreatedAtAsc(restaurantId, status);
        } else {
            return entryRepository.findByRestaurantIdOrderByCreatedAtAsc(restaurantId);
        }
    }

    @Transactional(readOnly = true)
    public List<WaitingListEntryEntity> getWaitingListByCustomer(UUID customerId) {
        return entryRepository.findByCustomerId(customerId);
    }

    @Transactional
    public WaitingListEntryEntity cancelWaitingListEntry(UUID entryId) {
        WaitingListEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waiting list entry not found: " + entryId));
        if ("WAITING".equals(entry.getStatus()) || "OFFERED".equals(entry.getStatus())) {
            entry.setStatus("CANCELLED");
            entryRepository.save(entry);
        }
        return entry;
    }

    @Transactional
    public void processCancellationOpening(UUID restaurantId, Instant cancelledStart, int partySize, List<UUID> releasedTableIds) {
        LocalDate date = cancelledStart.atZone(ZoneOffset.UTC).toLocalDate();
        LocalTime time = cancelledStart.atZone(ZoneOffset.UTC).toLocalTime();

        int releasedCapacity = (tableInventoryClient != null)
                ? tableInventoryClient.getReleasedCapacity(restaurantId, releasedTableIds)
                : 0;
        int effectiveCapacity = Math.max(partySize, releasedCapacity);

        // FIFO search for matching waiting list entries
        List<WaitingListEntryEntity> waiting = entryRepository.findByRestaurantIdAndTargetDateAndStatusOrderByCreatedAtAsc(
                restaurantId, date, "WAITING"
        );

        waiting.stream()
                .filter(entry -> entry.getPartySize() <= effectiveCapacity
                        && !time.isBefore(entry.getEarliestTime())
                        && !time.isAfter(entry.getLatestTime()))
                .findFirst()
                .ifPresent(entry -> {
                    entry.setStatus("OFFERED");
                    entryRepository.save(entry);

                    UUID offerId = UUID.randomUUID();
                    Instant now = Instant.now();
                    Instant expiresAt = now.plus(Duration.ofMinutes(15)); // 15-minute offer window
                    WaitingListOfferEntity offer = new WaitingListOfferEntity(
                            offerId, entry.getId(), restaurantId, cancelledStart, releasedTableIds, expiresAt, "PENDING", now, now
                    );
                    offerRepository.save(offer);

                    try {
                        WaitingListOfferCreatedEvent event = new WaitingListOfferCreatedEvent(
                                UUID.randomUUID(), now, offerId, entry.getId(), restaurantId, entry.getCustomerId(),
                                entry.getCustomerEmail(), cancelledStart, releasedTableIds, expiresAt
                        );
                        outboxRepository.save(new OutboxEventEntity(
                                UUID.randomUUID(), "WaitingListOffer", offerId.toString(), "WaitingListOfferCreated",
                                objectMapper.writeValueAsString(event), now
                        ));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to serialize offer event", e);
                    }
                });
    }

    @Transactional
    public WaitingListOfferEntity acceptOffer(UUID offerId) {
        WaitingListOfferEntity offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

        if (!"PENDING".equals(offer.getStatus())) {
            throw new IllegalStateException("Offer is not pending (status=" + offer.getStatus() + ")");
        }
        if (Instant.now().isAfter(offer.getExpiresAt())) {
            offer.setStatus("EXPIRED");
            offerRepository.save(offer);
            throw new IllegalStateException("Offer has expired");
        }

        offer.setStatus("ACCEPTED");
        offer.setUpdatedAt(Instant.now());
        offerRepository.save(offer);

        WaitingListEntryEntity entry = entryRepository.findById(offer.getWaitingListEntryId())
                .orElseThrow();
        entry.setStatus("CONVERTED");
        entryRepository.save(entry);

        try {
            WaitingListOfferAcceptedEvent event = new WaitingListOfferAcceptedEvent(
                    UUID.randomUUID(), Instant.now(), offerId, entry.getId(), UUID.randomUUID(),
                    offer.getRestaurantId(), entry.getCustomerId()
            );
            outboxRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(), "WaitingListOffer", offerId.toString(), "WaitingListOfferAccepted",
                    objectMapper.writeValueAsString(event), Instant.now()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize offer accepted event", e);
        }

        return offer;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void expireOffersAndCascade() {
        List<WaitingListOfferEntity> expired = offerRepository.findByStatusAndExpiresAtBefore("PENDING", Instant.now());
        for (WaitingListOfferEntity offer : expired) {
            offer.setStatus("EXPIRED");
            offer.setUpdatedAt(Instant.now());
            offerRepository.save(offer);

            WaitingListEntryEntity entry = entryRepository.findById(offer.getWaitingListEntryId()).orElse(null);
            if (entry != null) {
                entry.setStatus("EXPIRED");
                entryRepository.save(entry);
                // Cascade to next waiting customer
                processCancellationOpening(offer.getRestaurantId(), offer.getOfferedStartTime(), entry.getPartySize(), offer.getOfferedTableIds());
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEventEntity event : pending) {
            try {
                kafkaTemplate.send("waiting-list.events", event.getAggregateId(), event.getPayload());
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                break;
            }
        }
    }
}
