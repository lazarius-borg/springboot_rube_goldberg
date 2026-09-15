package nl.invokedynamic.demo.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.availability.domain.*;
import nl.invokedynamic.demo.availability.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    public AvailabilityService(RestaurantViewRepository restaurantRepository,
                               TableInventoryViewRepository tableRepository,
                               TableCombinationViewRepository combinationRepository,
                               SlotOccupancyViewRepository occupancyRepository,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.restaurantRepository = restaurantRepository;
        this.tableRepository = tableRepository;
        this.combinationRepository = combinationRepository;
        this.occupancyRepository = occupancyRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public AvailabilityResult checkAvailability(UUID restaurantId, LocalDate date, LocalTime time, int partySize) {
        String cacheKey = String.format("availability:%s:%s:%s:%d", restaurantId, date, time, partySize);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, AvailabilityResult.class);
            }
        } catch (Exception ignored) {}

        RestaurantViewEntity restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));

        ZoneId zone = ZoneId.of(restaurant.getTimezone());
        ZonedDateTime requestedZoned = ZonedDateTime.of(date, time, zone);
        Instant startTime = requestedZoned.toInstant();
        Instant endTime = startTime.plus(Duration.ofMinutes(restaurant.getDefaultReservationDurationMinutes()));

        List<TableInventoryViewEntity> tables = tableRepository.findByRestaurantId(restaurantId);
        List<TableCombinationViewEntity> combinations = combinationRepository.findByRestaurantId(restaurantId);
        List<SlotOccupancyViewEntity> occupancies = occupancyRepository.findByRestaurantIdAndStartTimeLessThanAndEndTimeGreaterThan(
                restaurantId, endTime, startTime
        );

        Set<UUID> occupiedTableIds = occupancies.stream()
                .map(SlotOccupancyViewEntity::getTableId)
                .collect(Collectors.toSet());

        boolean available = tables.stream()
                .anyMatch(t -> !occupiedTableIds.contains(t.getId()) && t.getCapacity() >= partySize)
                || combinations.stream()
                .filter(c -> c.getCombinedCapacity() >= partySize)
                .anyMatch(c -> c.getTableIds().stream().noneMatch(occupiedTableIds::contains));

        List<String> suggestedSlots = new ArrayList<>();
        if (available) {
            suggestedSlots.add(requestedZoned.toString());
        }

        AvailabilityResult result = new AvailabilityResult(restaurantId, requestedZoned.toString(), partySize, available, suggestedSlots);
        log.info("Checked availability for restaurant {} date {} time {} partySize {}: available={}",
                restaurantId, date, time, partySize, available);

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

    public record AvailabilityResult(UUID restaurantId, String requestedTime, int partySize, boolean isAvailable, List<String> availableSlots) {}
}
