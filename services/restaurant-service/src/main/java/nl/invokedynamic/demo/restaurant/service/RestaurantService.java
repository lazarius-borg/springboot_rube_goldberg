package nl.invokedynamic.demo.restaurant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.RestaurantCreatedEvent;
import nl.invokedynamic.demo.events.TableConfigurationChangedEvent;
import nl.invokedynamic.demo.restaurant.domain.*;
import nl.invokedynamic.demo.restaurant.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final OpeningHoursRepository openingHoursRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableCombinationRepository combinationRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             OpeningHoursRepository openingHoursRepository,
                             RestaurantTableRepository tableRepository,
                             TableCombinationRepository combinationRepository,
                             OutboxEventRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.restaurantRepository = restaurantRepository;
        this.openingHoursRepository = openingHoursRepository;
        this.tableRepository = tableRepository;
        this.combinationRepository = combinationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RestaurantEntity createRestaurant(String name, String address, String timezone,
                                             int durationMinutes, int minAdvanceMinutes,
                                             int maxHorizonDays, int cancellationWindowHours) {
        // Validate timezone
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IANA timezone: " + timezone);
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        RestaurantEntity entity = new RestaurantEntity(
                id, name, address, timezone, durationMinutes, minAdvanceMinutes, maxHorizonDays,
                cancellationWindowHours, "ACTIVE", now, now
        );
        restaurantRepository.save(entity);

        try {
            RestaurantCreatedEvent event = new RestaurantCreatedEvent(
                    UUID.randomUUID(), now, id, name, timezone,
                    durationMinutes, minAdvanceMinutes, maxHorizonDays, cancellationWindowHours
            );
            outboxRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(), "Restaurant", id.toString(), "RestaurantCreated",
                    objectMapper.writeValueAsString(event), now
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return entity;
    }

    public Page<RestaurantEntity> listRestaurants(Pageable pageable) {
        return restaurantRepository.findAll(pageable);
    }

    public Optional<RestaurantEntity> getRestaurant(UUID id) {
        return restaurantRepository.findById(id);
    }

    @Transactional
    public RestaurantTableEntity addTable(UUID restaurantId, String tableNumber, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Table capacity must be greater than 0");
        }
        RestaurantTableEntity table = new RestaurantTableEntity(
                UUID.randomUUID(), restaurantId, tableNumber, capacity, "ACTIVE", Instant.now()
        );
        tableRepository.save(table);
        publishTableConfigurationEvent(restaurantId);
        return table;
    }

    @Transactional
    public TableCombinationEntity addTableCombination(UUID restaurantId, String name, List<UUID> tableIds) {
        List<RestaurantTableEntity> tables = tableRepository.findAllById(tableIds);
        int totalCapacity = tables.stream().mapToInt(RestaurantTableEntity::getCapacity).sum();
        TableCombinationEntity combination = new TableCombinationEntity(
                UUID.randomUUID(), restaurantId, name, tableIds, totalCapacity
        );
        combinationRepository.save(combination);
        publishTableConfigurationEvent(restaurantId);
        return combination;
    }

    @Transactional
    public void configureOpeningHours(UUID restaurantId, List<OpeningHoursEntity> schedules) {
        List<OpeningHoursEntity> existing = openingHoursRepository.findByRestaurantId(restaurantId);
        openingHoursRepository.deleteAll(existing);
        openingHoursRepository.saveAll(schedules);
    }

    public List<OpeningHoursEntity> getOpeningHours(UUID restaurantId) {
        return openingHoursRepository.findByRestaurantId(restaurantId);
    }

    public List<RestaurantTableEntity> getTables(UUID restaurantId) {
        return tableRepository.findByRestaurantId(restaurantId);
    }

    public List<TableCombinationEntity> getTableCombinations(UUID restaurantId) {
        return combinationRepository.findByRestaurantId(restaurantId);
    }

    private void publishTableConfigurationEvent(UUID restaurantId) {
        try {
            List<RestaurantTableEntity> tables = tableRepository.findByRestaurantId(restaurantId);
            List<TableCombinationEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);

            List<TableConfigurationChangedEvent.TableConfig> tableConfigs = tables.stream()
                    .map(t -> new TableConfigurationChangedEvent.TableConfig(t.getId(), t.getTableNumber(), t.getCapacity()))
                    .toList();
            List<TableConfigurationChangedEvent.CombinationConfig> combConfigs = combinations.stream()
                    .map(c -> new TableConfigurationChangedEvent.CombinationConfig(c.getId(), c.getName(), c.getTableIds(), c.getCombinedCapacity()))
                    .toList();

            TableConfigurationChangedEvent event = new TableConfigurationChangedEvent(
                    UUID.randomUUID(), Instant.now(), restaurantId, tableConfigs, combConfigs
            );

            outboxRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(), "Restaurant", restaurantId.toString(), "TableConfigurationChanged",
                    objectMapper.writeValueAsString(event), Instant.now()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize table config event", e);
        }
    }
}
