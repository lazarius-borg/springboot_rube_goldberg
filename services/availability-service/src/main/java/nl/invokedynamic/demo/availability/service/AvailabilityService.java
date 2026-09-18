package nl.invokedynamic.demo.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.availability.client.RestaurantScheduleClient;
import nl.invokedynamic.demo.availability.domain.*;
import nl.invokedynamic.demo.availability.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final RestaurantViewRepository restaurantRepository;
    private final TableInventoryViewRepository tableRepository;
    private final TableCombinationViewRepository combinationRepository;
    private final SlotOccupancyViewRepository occupancyRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestaurantTimezoneResolver timezoneResolver;
    private final RestaurantScheduleClient scheduleClient;

    public AvailabilityService(RestaurantViewRepository restaurantRepository,
                               TableInventoryViewRepository tableRepository,
                               TableCombinationViewRepository combinationRepository,
                               SlotOccupancyViewRepository occupancyRepository,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               RestaurantTimezoneResolver timezoneResolver,
                               RestaurantScheduleClient scheduleClient) {
        this.restaurantRepository = restaurantRepository;
        this.tableRepository = tableRepository;
        this.combinationRepository = combinationRepository;
        this.occupancyRepository = occupancyRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.timezoneResolver = timezoneResolver;
        this.scheduleClient = scheduleClient;
    }

    public AvailabilityResult checkAvailability(UUID restaurantId, LocalDate date, LocalTime time, int partySize) {
        RestaurantViewEntity restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));

        ZoneId zone = timezoneResolver.resolveZoneId(restaurant);
        ZonedDateTime requestedZoned = ZonedDateTime.of(date, time, zone);

        // Role-based temporal validation
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isManagerOrAdmin = auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RESTAURANT_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));

        if (!isManagerOrAdmin) {
            ZonedDateTime nowInZone = ZonedDateTime.now(zone);
            int minAdvance = restaurant.getMinBookingAdvanceMinutes();
            ZonedDateTime earliestAllowed = nowInZone.plusMinutes(minAdvance);
            if (requestedZoned.isBefore(earliestAllowed.minusMinutes(5))) {
                if (requestedZoned.isBefore(nowInZone.minusMinutes(5))) {
                    throw new IllegalArgumentException("Dining time cannot be in the past");
                } else {
                    throw new IllegalArgumentException("Dining time must be at least " + minAdvance + " minutes in advance");
                }
            }
            if (requestedZoned.isAfter(nowInZone.plusDays(365))) {
                throw new IllegalArgumentException("Dining date cannot be more than 365 days in advance");
            }
        }

        String cacheKey = String.format("availability:%s:%s:%s:%d", restaurantId, date, time, partySize);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, AvailabilityResult.class);
            }
        } catch (Exception ignored) {}

        List<TableInventoryViewEntity> tables = tableRepository.findByRestaurantId(restaurantId);
        List<TableCombinationViewEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);

        int totalRestaurantCapacity = tables.stream().mapToInt(TableInventoryViewEntity::getCapacity).sum();
        int maxSingleTableCapacity = tables.stream().mapToInt(TableInventoryViewEntity::getCapacity).max().orElse(0);
        int maxComboCapacity = combinations.stream().mapToInt(TableCombinationViewEntity::getCombinedCapacity).max().orElse(0);
        int maxTableCapacity = Math.max(maxSingleTableCapacity, maxComboCapacity);

        RestaurantScheduleClient.OpeningHoursResponse schedule = scheduleClient != null
                ? scheduleClient.getScheduleFor(restaurantId, date)
                : null;
        if (schedule != null) {
            if (schedule.isClosed()) {
                AvailabilityResult result = new AvailabilityResult(
                        restaurantId, requestedZoned.toString(), partySize, false, true, "RESTAURANT_CLOSED",
                        List.of(), maxTableCapacity, totalRestaurantCapacity
                );
                log.info("Restaurant {} is closed on date {}", restaurantId, date);
                return cacheAndReturn(cacheKey, result);
            }
            if (schedule.openTime() != null && schedule.closeTime() != null) {
                if (time.isBefore(schedule.openTime())) {
                    throw new IllegalArgumentException("Seating time " + time + " is before opening time of " + schedule.openTime());
                }
                if (!time.isBefore(schedule.closeTime())) {
                    throw new IllegalArgumentException("Seating time " + time + " is at or after closing time of " + schedule.closeTime());
                }
                int minDuration = restaurant.getMinReservationDurationMinutes() > 0 ? restaurant.getMinReservationDurationMinutes() : 45;
                LocalTime latestSeating = schedule.closeTime().minusMinutes(minDuration);
                if (time.isAfter(latestSeating)) {
                    throw new IllegalArgumentException("Seating time " + time + " is too close to closing time (" + schedule.closeTime() + "); latest seating allowed is " + latestSeating + " for the minimum dining duration of " + minDuration + " minutes");
                }
            }
        } else if (scheduleClient != null && scheduleClient.isClosedAt(restaurantId, date, time)) {
            AvailabilityResult result = new AvailabilityResult(
                    restaurantId, requestedZoned.toString(), partySize, false, true, "RESTAURANT_CLOSED",
                    List.of(), maxTableCapacity, totalRestaurantCapacity
            );
            log.info("Restaurant {} is closed on date {} time {}", restaurantId, date, time);
            return cacheAndReturn(cacheKey, result);
        }

        if (totalRestaurantCapacity > 0 && partySize > totalRestaurantCapacity) {
            AvailabilityResult result = new AvailabilityResult(
                    restaurantId, requestedZoned.toString(), partySize, false, false, "EXCEEDS_TOTAL_CAPACITY",
                    List.of(), maxTableCapacity, totalRestaurantCapacity
            );
            log.info("Party size {} exceeds total restaurant capacity {} for restaurant {}", partySize, totalRestaurantCapacity, restaurantId);
            return cacheAndReturn(cacheKey, result);
        }

        Instant startTime = requestedZoned.toInstant();
        Instant endTime = startTime.plus(Duration.ofMinutes(restaurant.getDefaultReservationDurationMinutes()));

        List<SlotOccupancyViewEntity> occupancies = occupancyRepository.findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(
                restaurantId, endTime, startTime
        );

        Set<UUID> occupiedTableIds = occupancies.stream()
                .map(SlotOccupancyViewEntity::getTableId)
                .collect(Collectors.toSet());

        List<TableInventoryViewEntity> freeTables = tables.stream()
                .filter(t -> !occupiedTableIds.contains(t.getId()))
                .toList();
        int totalFreeCapacity = freeTables.stream().mapToInt(TableInventoryViewEntity::getCapacity).sum();

        boolean singleOrComboAvailable = freeTables.stream()
                .anyMatch(t -> t.getCapacity() >= partySize)
                || combinations.stream()
                .filter(c -> c.getCombinedCapacity() >= partySize)
                .anyMatch(c -> c.getTableIds().stream().noneMatch(occupiedTableIds::contains));

        boolean available = singleOrComboAvailable || totalFreeCapacity >= partySize;
        String reason = available ? null : "NO_TABLES_AVAILABLE";

        List<String> suggestedSlots = new ArrayList<>();
        if (available) {
            suggestedSlots.add(requestedZoned.toString());
        }

        AvailabilityResult result = new AvailabilityResult(
                restaurantId, requestedZoned.toString(), partySize, available, false, reason,
                suggestedSlots, maxTableCapacity, totalRestaurantCapacity
        );
        log.info("Checked availability for restaurant {} date {} time {} partySize {}: available={}, reason={}",
                restaurantId, date, time, partySize, available, reason);

        return cacheAndReturn(cacheKey, result);
    }

    private AvailabilityResult cacheAndReturn(String cacheKey, AvailabilityResult result) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), 60, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return result;
    }

    public void invalidateCache(UUID restaurantId) {
        log.info("Invalidating availability cache for restaurant {}", restaurantId);
        try {
            Set<String> keys = redisTemplate.keys(String.format("availability:%s:*", restaurantId));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {}
    }

    public record AvailabilityResult(
            UUID restaurantId,
            String requestedTime,
            int partySize,
            boolean isAvailable,
            boolean isClosed,
            String reason,
            List<String> availableSlots,
            int maxTableCapacity,
            int totalRestaurantCapacity
    ) {
        public AvailabilityResult(UUID restaurantId, String requestedTime, int partySize, boolean isAvailable, List<String> availableSlots) {
            this(restaurantId, requestedTime, partySize, isAvailable, false, null, availableSlots, 0, 0);
        }
    }
}
