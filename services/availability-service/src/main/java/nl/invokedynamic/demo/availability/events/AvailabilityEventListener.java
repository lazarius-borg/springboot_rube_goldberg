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

@Component
public class AvailabilityEventListener {

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
            if (message.contains("RestaurantCreated")) {
                RestaurantCreatedEvent event = objectMapper.readValue(message, RestaurantCreatedEvent.class);
                restaurantRepository.save(new RestaurantViewEntity(
                        event.restaurantId(), event.name(), event.timezone(),
                        event.defaultReservationDurationMinutes(), event.minBookingAdvanceMinutes(),
                        event.maxBookingHorizonDays(), event.cancellationWindowHours(), Instant.now()
                ));
            } else if (message.contains("TableConfigurationChanged")) {
                TableConfigurationChangedEvent event = objectMapper.readValue(message, TableConfigurationChangedEvent.class);
                tableRepository.deleteAll(tableRepository.findByRestaurantId(event.restaurantId()));
                combinationRepository.deleteAll(combinationRepository.findByRestaurantId(event.restaurantId()));

                for (var t : event.tables()) {
                    tableRepository.save(new TableInventoryViewEntity(t.tableId(), event.restaurantId(), t.tableNumber(), t.capacity()));
                }
                for (var c : event.combinations()) {
                    combinationRepository.save(new TableCombinationViewEntity(c.combinationId(), event.restaurantId(), c.name(), c.tableIds(), c.combinedCapacity()));
                }
                availabilityService.invalidateCache(event.restaurantId());
            }
        } catch (Exception ignored) {}
    }

    @KafkaListener(topics = "reservation.events", groupId = "availability-service-group")
    @Transactional
    public void handleReservationEvent(String message) {
        try {
            if (message.contains("ReservationCreated")) {
                ReservationCreatedEvent event = objectMapper.readValue(message, ReservationCreatedEvent.class);
                for (UUID tableId : event.allocatedTableIds()) {
                    occupancyRepository.save(new SlotOccupancyViewEntity(
                            UUID.randomUUID(), event.reservationId(), event.restaurantId(), tableId, event.startTime(), event.endTime()
                    ));
                }
                availabilityService.invalidateCache(event.restaurantId());
            } else if (message.contains("ReservationCancelled")) {
                ReservationCancelledEvent event = objectMapper.readValue(message, ReservationCancelledEvent.class);
                occupancyRepository.deleteByReservationId(event.reservationId());
                availabilityService.invalidateCache(event.restaurantId());
            }
        } catch (Exception ignored) {}
    }
}
