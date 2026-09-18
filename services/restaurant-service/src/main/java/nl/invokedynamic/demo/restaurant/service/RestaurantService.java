package nl.invokedynamic.demo.restaurant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.events.RestaurantCreatedEvent;
import nl.invokedynamic.demo.events.TableConfigurationChangedEvent;
import nl.invokedynamic.demo.restaurant.api.dto.TableCombinationResponse;
import nl.invokedynamic.demo.restaurant.api.dto.UpdateRestaurantSettingsRequest;
import nl.invokedynamic.demo.restaurant.client.ReservationClient;
import nl.invokedynamic.demo.restaurant.domain.*;
import nl.invokedynamic.demo.restaurant.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final OpeningHoursRepository openingHoursRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableCombinationRepository combinationRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ReservationClient reservationClient;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             OpeningHoursRepository openingHoursRepository,
                             RestaurantTableRepository tableRepository,
                             TableCombinationRepository combinationRepository,
                             OutboxEventRepository outboxRepository,
                             ObjectMapper objectMapper,
                             ReservationClient reservationClient) {
        this.restaurantRepository = restaurantRepository;
        this.openingHoursRepository = openingHoursRepository;
        this.tableRepository = tableRepository;
        this.combinationRepository = combinationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.reservationClient = reservationClient;
    }

    @Transactional
    public RestaurantEntity createRestaurant(String name, String address, String timezone,
                                             int durationMinutes, int minAdvanceMinutes,
                                             int maxHorizonDays, int cancellationWindowHours) {
        return createRestaurant(name, address, timezone, 45, durationMinutes, 180, minAdvanceMinutes, maxHorizonDays, cancellationWindowHours);
    }

    @Transactional
    public RestaurantEntity createRestaurant(String name, String address, String timezone,
                                             int durationMinutes, int maxDurationMinutes, int minAdvanceMinutes,
                                             int maxHorizonDays, int cancellationWindowHours) {
        return createRestaurant(name, address, timezone, 45, durationMinutes, maxDurationMinutes, minAdvanceMinutes, maxHorizonDays, cancellationWindowHours);
    }

    @Transactional
    public RestaurantEntity createRestaurant(String name, String address, String timezone,
                                             int minDurationMinutes, int durationMinutes, int maxDurationMinutes,
                                             int minAdvanceMinutes, int maxHorizonDays, int cancellationWindowHours) {
        // Validate timezone
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IANA timezone: " + timezone);
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        RestaurantEntity entity = new RestaurantEntity(
                id, name, address, timezone,
                minDurationMinutes > 0 ? minDurationMinutes : 45,
                durationMinutes,
                maxDurationMinutes > 0 ? maxDurationMinutes : 180,
                minAdvanceMinutes, maxHorizonDays, cancellationWindowHours, "ACTIVE", now, now
        );
        restaurantRepository.save(entity);

        try {
            RestaurantCreatedEvent event = new RestaurantCreatedEvent(
                    UUID.randomUUID(), now, id, name, timezone,
                    entity.getMinReservationDurationMinutes(),
                    durationMinutes,
                    entity.getMaxReservationDurationMinutes(),
                    minAdvanceMinutes, maxHorizonDays, cancellationWindowHours
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

    @Transactional
    public RestaurantEntity updateRestaurantSettings(UUID restaurantId, UpdateRestaurantSettingsRequest req) {
        RestaurantEntity entity = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));

        if (req.name() != null && !req.name().isBlank()) {
            entity.setName(req.name());
        }
        if (req.address() != null && !req.address().isBlank()) {
            entity.setAddress(req.address());
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            try {
                ZoneId.of(req.timezone());
                entity.setTimezone(req.timezone());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid IANA timezone: " + req.timezone());
            }
        }
        if (req.minReservationDurationMinutes() != null) {
            entity.setMinReservationDurationMinutes(req.minReservationDurationMinutes());
        }
        if (req.defaultReservationDurationMinutes() != null) {
            entity.setDefaultReservationDurationMinutes(req.defaultReservationDurationMinutes());
        }
        if (req.maxReservationDurationMinutes() != null) {
            entity.setMaxReservationDurationMinutes(req.maxReservationDurationMinutes());
        }
        if (req.minBookingAdvanceMinutes() != null) {
            entity.setMinBookingAdvanceMinutes(req.minBookingAdvanceMinutes());
        }
        if (req.maxBookingHorizonDays() != null) {
            entity.setMaxBookingHorizonDays(req.maxBookingHorizonDays());
        }
        if (req.cancellationWindowHours() != null) {
            entity.setCancellationWindowHours(req.cancellationWindowHours());
        }
        entity.setUpdatedAt(Instant.now());
        return restaurantRepository.save(entity);
    }

    public Page<RestaurantEntity> listRestaurants(Pageable pageable) {
        return restaurantRepository.findAll(pageable);
    }

    public Optional<RestaurantEntity> getRestaurant(UUID id) {
        return restaurantRepository.findById(id);
    }

    @Transactional
    public RestaurantTableEntity addTable(UUID restaurantId, String tableNumber, int capacity) {
        return addTable(restaurantId, tableNumber, capacity, "Main Dining");
    }

    @Transactional
    public RestaurantTableEntity addTable(UUID restaurantId, String tableNumber, int capacity, String zone) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Table capacity must be greater than 0");
        }
        if (tableNumber == null || tableNumber.isBlank()) {
            throw new IllegalArgumentException("Table number cannot be blank");
        }
        String resolvedZone = (zone != null && !zone.isBlank()) ? zone : "Main Dining";
        RestaurantTableEntity table = new RestaurantTableEntity(
                UUID.randomUUID(), restaurantId, tableNumber, capacity, resolvedZone, "ACTIVE", Instant.now()
        );
        tableRepository.save(table);
        publishTableConfigurationEvent(restaurantId);
        return table;
    }

    @Transactional
    public RestaurantTableEntity updateTable(UUID restaurantId, UUID tableId, String tableNumber, Integer capacity, String zone) {
        RestaurantTableEntity table = tableRepository.findById(tableId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new NoSuchElementException("Table not found: " + tableId));

        if (tableNumber != null && !tableNumber.isBlank()) {
            table.setTableNumber(tableNumber);
        }
        boolean capacityChanged = false;
        if (capacity != null) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Table capacity must be greater than 0");
            }
            if (table.getCapacity() != capacity) {
                table.setCapacity(capacity);
                capacityChanged = true;
            }
        }
        boolean zoneChanged = false;
        if (zone != null && !zone.isBlank()) {
            String trimmedZone = zone.trim();
            if (table.getZone() == null || !table.getZone().equalsIgnoreCase(trimmedZone)) {
                table.setZone(trimmedZone);
                zoneChanged = true;
            }
        }
        tableRepository.save(table);

        if (capacityChanged) {
            List<TableCombinationEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);
            for (TableCombinationEntity comb : combinations) {
                if (comb.getTableIds().contains(tableId)) {
                    List<RestaurantTableEntity> constituentTables = tableRepository.findAllById(comb.getTableIds());
                    int newTotal = constituentTables.stream().mapToInt(RestaurantTableEntity::getCapacity).sum();
                    comb.setCombinedCapacity(newTotal);
                    combinationRepository.save(comb);
                }
            }
        }

        if (zoneChanged) {
            List<TableCombinationEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);
            List<RestaurantTableEntity> allTables = tableRepository.findByRestaurantId(restaurantId);
            Map<UUID, String> zoneMap = allTables.stream()
                    .collect(Collectors.toMap(RestaurantTableEntity::getId, t -> t.getZone() != null ? t.getZone().trim() : "Main Dining Room"));
            zoneMap.put(tableId, table.getZone() != null ? table.getZone().trim() : "Main Dining Room");

            List<TableCombinationEntity> invalidCombos = combinations.stream()
                    .filter(c -> c.getTableIds().contains(tableId))
                    .filter(c -> {
                        Set<String> zones = c.getTableIds().stream()
                                .map(id -> zoneMap.getOrDefault(id, "Main Dining Room").toLowerCase())
                                .collect(Collectors.toSet());
                        return zones.size() > 1;
                    })
                    .toList();
            if (!invalidCombos.isEmpty()) {
                combinationRepository.deleteAll(invalidCombos);
            }
        }

        publishTableConfigurationEvent(restaurantId);
        return table;
    }

    @Transactional
    public void deleteTable(UUID restaurantId, UUID tableId) {
        RestaurantTableEntity table = tableRepository.findById(tableId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new NoSuchElementException("Table not found: " + tableId));

        if (reservationClient.hasActiveUpcomingReservations(restaurantId, tableId)) {
            throw new IllegalStateException("Table cannot be deleted because it is allocated to active upcoming reservations");
        }

        List<TableCombinationEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);
        List<TableCombinationEntity> affectedCombinations = combinations.stream()
                .filter(c -> c.getTableIds().contains(tableId))
                .toList();
        if (!affectedCombinations.isEmpty()) {
            combinationRepository.deleteAll(affectedCombinations);
        }

        tableRepository.delete(table);
        publishTableConfigurationEvent(restaurantId);
    }

    @Transactional
    public TableCombinationEntity addTableCombination(UUID restaurantId, String name, List<UUID> tableIds) {
        return addTableCombination(restaurantId, name, tableIds, null);
    }

    @Transactional
    public TableCombinationEntity addTableCombination(UUID restaurantId, String name, List<UUID> tableIds, Integer combinedCapacity) {
        if (tableIds == null || tableIds.size() < 2) {
            throw new IllegalArgumentException("Table combination must contain at least 2 tables");
        }
        List<RestaurantTableEntity> tables = tableRepository.findAllById(tableIds);
        if (tables.size() != tableIds.size()) {
            throw new IllegalArgumentException("One or more tables not found for combination");
        }
        for (RestaurantTableEntity t : tables) {
            if (!t.getRestaurantId().equals(restaurantId)) {
                throw new IllegalArgumentException("Table " + t.getTableNumber() + " does not belong to restaurant " + restaurantId);
            }
        }

        // Same-zone validation
        String firstZone = tables.get(0).getZone() != null ? tables.get(0).getZone().trim() : "Main Dining Room";
        boolean sameZone = tables.stream().allMatch(t -> {
            String z = t.getZone() != null ? t.getZone().trim() : "Main Dining Room";
            return z.equalsIgnoreCase(firstZone);
        });
        if (!sameZone) {
            throw new IllegalArgumentException("All combined tables must reside in the same floor zone");
        }

        // Duplicate combination detection (order-insensitive set equality)
        Set<UUID> newTableIdSet = new HashSet<>(tableIds);
        List<TableCombinationEntity> existingCombinations = combinationRepository.findByRestaurantId(restaurantId);
        boolean duplicateExists = existingCombinations.stream()
                .anyMatch(c -> new HashSet<>(c.getTableIds()).equals(newTableIdSet));
        if (duplicateExists) {
            throw new IllegalArgumentException("A table combination containing the exact same tables already exists");
        }

        // Capacity validation & defaulting
        int physicalSum = tables.stream().mapToInt(RestaurantTableEntity::getCapacity).sum();
        int totalCapacity;
        if (combinedCapacity != null && combinedCapacity > 0) {
            if (combinedCapacity > physicalSum) {
                throw new IllegalArgumentException("Combined capacity (" + combinedCapacity + ") cannot exceed the sum of physical table capacities (" + physicalSum + ")");
            }
            totalCapacity = combinedCapacity;
        } else {
            totalCapacity = physicalSum;
        }

        String resolvedName = (name != null && !name.isBlank())
                ? name.trim()
                : "Combo: " + tables.stream().map(RestaurantTableEntity::getTableNumber).collect(Collectors.joining(" + "));

        TableCombinationEntity combination = new TableCombinationEntity(
                UUID.randomUUID(), restaurantId, resolvedName, firstZone, tableIds, totalCapacity
        );
        combinationRepository.save(combination);
        publishTableConfigurationEvent(restaurantId);
        return combination;
    }

    @Transactional
    public TableCombinationEntity updateTableCombination(UUID restaurantId, UUID combinationId, String name, Integer combinedCapacity) {
        TableCombinationEntity comb = combinationRepository.findByIdAndRestaurantId(combinationId, restaurantId)
                .orElseThrow(() -> new NoSuchElementException("Table combination not found: " + combinationId));

        if (name != null && !name.trim().isBlank()) {
            comb.setName(name.trim());
        }

        if (combinedCapacity != null) {
            List<RestaurantTableEntity> tables = tableRepository.findAllById(comb.getTableIds());
            int physicalSum = tables.stream().mapToInt(RestaurantTableEntity::getCapacity).sum();
            if (combinedCapacity <= 0) {
                throw new IllegalArgumentException("Combined capacity must be greater than 0");
            }
            if (combinedCapacity > physicalSum) {
                throw new IllegalArgumentException("Custom capacity (" + combinedCapacity + ") cannot exceed the sum of member table capacities (" + physicalSum + ")");
            }
            comb.setCombinedCapacity(combinedCapacity);
        }

        combinationRepository.save(comb);
        publishTableConfigurationEvent(restaurantId);
        return comb;
    }

    @Transactional
    public void deleteTableCombination(UUID restaurantId, UUID combinationId) {
        TableCombinationEntity comb = combinationRepository.findByIdAndRestaurantId(combinationId, restaurantId)
                .orElseThrow(() -> new NoSuchElementException("Table combination not found: " + combinationId));
        combinationRepository.delete(comb);
        publishTableConfigurationEvent(restaurantId);
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

    public List<TableCombinationResponse> getTableCombinationResponses(UUID restaurantId) {
        List<TableCombinationEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);
        List<RestaurantTableEntity> tables = tableRepository.findByRestaurantId(restaurantId);
        Map<UUID, String> tableNumberMap = tables.stream()
                .collect(Collectors.toMap(RestaurantTableEntity::getId, RestaurantTableEntity::getTableNumber));
        return combinations.stream().map(c -> new TableCombinationResponse(
                c.getId(),
                c.getRestaurantId(),
                c.getName(),
                c.getZone() != null ? c.getZone() : "Main Dining Room",
                c.getTableIds(),
                c.getTableIds().stream().map(id -> tableNumberMap.getOrDefault(id, id.toString())).toList(),
                c.getCombinedCapacity()
        )).toList();
    }

    public TableCombinationResponse toResponse(TableCombinationEntity c) {
        List<RestaurantTableEntity> tables = tableRepository.findByRestaurantId(c.getRestaurantId());
        Map<UUID, String> tableNumberMap = tables.stream()
                .collect(Collectors.toMap(RestaurantTableEntity::getId, RestaurantTableEntity::getTableNumber));
        return new TableCombinationResponse(
                c.getId(),
                c.getRestaurantId(),
                c.getName(),
                c.getZone() != null ? c.getZone() : "Main Dining Room",
                c.getTableIds(),
                c.getTableIds().stream().map(id -> tableNumberMap.getOrDefault(id, id.toString())).toList(),
                c.getCombinedCapacity()
        );
    }

    private void publishTableConfigurationEvent(UUID restaurantId) {
        try {
            List<RestaurantTableEntity> tables = tableRepository.findByRestaurantId(restaurantId);
            List<TableCombinationEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);

            List<TableConfigurationChangedEvent.TableConfig> tableConfigs = tables.stream()
                    .map(t -> new TableConfigurationChangedEvent.TableConfig(t.getId(), t.getTableNumber(), t.getCapacity(), t.getZone()))
                    .toList();
            List<TableConfigurationChangedEvent.CombinationConfig> combConfigs = combinations.stream()
                    .map(c -> new TableConfigurationChangedEvent.CombinationConfig(
                            c.getId(), c.getName(), c.getZone() != null ? c.getZone() : "Main Dining Room", c.getTableIds(), c.getCombinedCapacity()
                    ))
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
