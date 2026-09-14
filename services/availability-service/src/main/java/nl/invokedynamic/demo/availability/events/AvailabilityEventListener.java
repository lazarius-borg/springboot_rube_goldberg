package nl.invokedynamic.demo.availability.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.availability.domain.*;
import nl.invokedynamic.demo.availability.repository.*;
import nl.invokedynamic.demo.availability.service.AvailabilityService;
import nl.invokedynamic.demo.events.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AvailabilityEventListener {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityEventListener.class);

    private final RestaurantViewRepository restaurantRepository;
    private final TableInventoryViewRepository tableRepository;
    private final TableCombinationViewRepository combinationRepository;
    private final SlotOccupancyViewRepository occupancyRepository;
    private final AvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    public AvailabilityEventListener(RestaurantViewRepository restaurantRepository,
                                     TableInventoryViewRepository tableRepository,
                                     TableCombinationViewRepository combinationRepository,
                                     SlotOccupancyViewRepository occupancyRepository,
                                     AvailabilityService availabilityService,
                                     ObjectMapper objectMapper) {
        this.restaurantRepository = restaurantRepository;
        this.tableRepository = tableRepository;
        this.combinationRepository = combinationRepository;
        this.occupancyRepository = occupancyRepository;
        this.availabilityService = availabilityService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "restaurant.events", groupId = "availability-service-group")
    @Transactional
    public void handleRestaurantEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
            if (node.has("defaultReservationDurationMinutes") || node.has("minBookingAdvanceMinutes") || message.contains("RestaurantCreated")) {
                RestaurantCreatedEvent event = objectMapper.treeToValue(node, RestaurantCreatedEvent.class);
                restaurantRepository.save(new RestaurantViewEntity(
                        event.restaurantId(), event.name(), event.timezone(),
                        event.defaultReservationDurationMinutes(), event.minBookingAdvanceMinutes(),
                        event.maxBookingHorizonDays(), event.cancellationWindowHours(), Instant.now()
                ));
                log.info("Handled RestaurantCreatedEvent in availability-service for restaurantId: {}", event.restaurantId());
            } else if (node.has("tables") || node.has("combinations") || message.contains("TableConfigurationChanged")) {
                TableConfigurationChangedEvent event = objectMapper.treeToValue(node, TableConfigurationChangedEvent.class);
                tableRepository.deleteAll(tableRepository.findByRestaurantId(event.restaurantId()));
                combinationRepository.deleteAll(combinationRepository.findByRestaurantId(event.restaurantId()));

                for (var t : event.tables()) {
                    tableRepository.save(new TableInventoryViewEntity(t.tableId(), event.restaurantId(), t.tableNumber(), t.capacity()));
                }
                for (var c : event.combinations()) {
                    combinationRepository.save(new TableCombinationViewEntity(c.combinationId(), event.restaurantId(), c.name(), c.tableIds(), c.combinedCapacity()));
                }
                availabilityService.invalidateCache(event.restaurantId());
                log.info("Handled TableConfigurationChangedEvent in availability-service for restaurantId: {}", event.restaurantId());
            }
        } catch (Exception e) {
            log.error("Error processing restaurant event in availability-service: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "reservation.events", groupId = "availability-service-group")
    @Transactional
    public void handleReservationEvent(String message) {
        try {
            if (message != null && message.startsWith("\"") && message.endsWith("\"")) {
                message = objectMapper.readValue(message, String.class);
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(message);
            if (node.has("allocatedTableIds") || message.contains("ReservationCreated")) {
                ReservationCreatedEvent event = objectMapper.treeToValue(node, ReservationCreatedEvent.class);
                for (UUID tableId : event.allocatedTableIds()) {
                    occupancyRepository.save(new SlotOccupancyViewEntity(
                            UUID.randomUUID(), event.reservationId(), event.restaurantId(), tableId, event.startTime(), event.endTime()
                    ));
                }
                availabilityService.invalidateCache(event.restaurantId());
                log.info("Handled ReservationCreatedEvent in availability-service for reservationId: {}", event.reservationId());
            } else if (node.has("releasedTableIds") || node.has("reason") || message.contains("ReservationCancelled")) {
                ReservationCancelledEvent event = objectMapper.treeToValue(node, ReservationCancelledEvent.class);
                occupancyRepository.deleteByReservationId(event.reservationId());
                availabilityService.invalidateCache(event.restaurantId());
                log.info("Handled ReservationCancelledEvent in availability-service for reservationId: {}", event.reservationId());
            }
        } catch (Exception e) {
            log.error("Error processing reservation event in availability-service: {}", e.getMessage(), e);
        }
    }
}
