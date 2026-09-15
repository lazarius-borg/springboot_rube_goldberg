package nl.invokedynamic.demo.analytics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import nl.invokedynamic.demo.analytics.domain.ReservationDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.domain.ReservationHourlyMetricsEntity;
import nl.invokedynamic.demo.analytics.domain.WaitingListDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.repository.ReservationDailyMetricsRepository;
import nl.invokedynamic.demo.analytics.repository.ReservationHourlyMetricsRepository;
import nl.invokedynamic.demo.analytics.repository.WaitingListDailyMetricsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final ReservationDailyMetricsRepository resMetricsRepository;
    private final WaitingListDailyMetricsRepository waitMetricsRepository;
    private final ReservationHourlyMetricsRepository hourlyMetricsRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public AnalyticsService(ReservationDailyMetricsRepository resMetricsRepository,
                            WaitingListDailyMetricsRepository waitMetricsRepository,
                            ReservationHourlyMetricsRepository hourlyMetricsRepository,
                            MeterRegistry meterRegistry) {
        this.resMetricsRepository = resMetricsRepository;
        this.waitMetricsRepository = waitMetricsRepository;
        this.hourlyMetricsRepository = hourlyMetricsRepository;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    public AnalyticsService(ReservationDailyMetricsRepository resMetricsRepository,
                            WaitingListDailyMetricsRepository waitMetricsRepository) {
        this(resMetricsRepository, waitMetricsRepository, null, new SimpleMeterRegistry());
    }

    @Transactional
    public void recordReservationCreated(UUID restaurantId, Instant startTime, int partySize) {
        LocalDate date = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        int hour = startTime.atZone(ZoneOffset.UTC).getHour();
        DayOfWeek dayOfWeek = startTime.atZone(ZoneOffset.UTC).getDayOfWeek();

        recordReservationCreated(restaurantId, date, partySize);

        if (hourlyMetricsRepository != null) {
            ReservationHourlyMetricsEntity hourlyEntity = hourlyMetricsRepository
                    .findByRestaurantIdAndMetricDateAndHourOfDay(restaurantId, date, hour)
                    .orElseGet(() -> new ReservationHourlyMetricsEntity(UUID.randomUUID(), restaurantId, date, hour));
            hourlyEntity.incrementCount();
            hourlyMetricsRepository.save(hourlyEntity);
        }

        String bucket = partySize <= 6 ? String.valueOf(partySize) : "7+";
        String hourStr = String.format("%02d", hour);

        incrementCounter("reservation_demand_total",
                "restaurant_id", restaurantId.toString(),
                "day_of_week", dayOfWeek.name(),
                "hour", hourStr);

        incrementCounter("reservation_party_size_total",
                "restaurant_id", restaurantId.toString(),
                "party_bucket", bucket);

        incrementCounter("reservation_status_total",
                "restaurant_id", restaurantId.toString(),
                "status", "CREATED");
    }

    @Transactional
    public void recordReservationCreated(UUID restaurantId, LocalDate date, int partySize) {
        ReservationDailyMetricsEntity entity = resMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new ReservationDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementCreated(partySize);
        resMetricsRepository.save(entity);
    }

    @Transactional
    public void recordReservationCancelled(UUID restaurantId, LocalDate date, String reason) {
        String category = mapCancellationCategory(reason);

        ReservationDailyMetricsEntity entity = resMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new ReservationDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementCancelled(category);
        resMetricsRepository.save(entity);

        incrementCounter("reservation_cancellations_total",
                "restaurant_id", restaurantId.toString(),
                "category", category);

        incrementCounter("reservation_status_total",
                "restaurant_id", restaurantId.toString(),
                "status", "CANCELLED");
    }

    @Transactional
    public void recordReservationCancelled(UUID restaurantId, LocalDate date) {
        recordReservationCancelled(restaurantId, date, "CUSTOMER_REQUEST");
    }

    @Transactional
    public void recordWaitingListOfferAccepted(UUID restaurantId, LocalDate date) {
        WaitingListDailyMetricsEntity entity = waitMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new WaitingListDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementOffersAccepted();
        waitMetricsRepository.save(entity);

        incrementCounter("waiting_list_offers_accepted_total",
                "restaurant_id", restaurantId.toString());
    }

    @Transactional
    public void recordWaitingListEntryCreated(UUID restaurantId, LocalDate date) {
        WaitingListDailyMetricsEntity entity = waitMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new WaitingListDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementEntries();
        waitMetricsRepository.save(entity);

        incrementCounter("waiting_list_entries_created_total",
                "restaurant_id", restaurantId.toString());
    }

    public AnalyticsSummary getSummary(UUID restaurantId) {
        List<ReservationDailyMetricsEntity> resList = restaurantId != null ?
                resMetricsRepository.findByRestaurantId(restaurantId) : resMetricsRepository.findAll();
        List<WaitingListDailyMetricsEntity> waitList = restaurantId != null ?
                waitMetricsRepository.findByRestaurantId(restaurantId) : waitMetricsRepository.findAll();

        long totalReservations = resList.stream().mapToLong(ReservationDailyMetricsEntity::getReservationsCreatedCount).sum();
        long completed = resList.stream().mapToLong(ReservationDailyMetricsEntity::getReservationsCompletedCount).sum();
        long cancelled = resList.stream().mapToLong(ReservationDailyMetricsEntity::getReservationsCancelledCount).sum();
        long noShows = resList.stream().mapToLong(ReservationDailyMetricsEntity::getReservationsNoShowCount).sum();
        long totalGuests = resList.stream().mapToLong(ReservationDailyMetricsEntity::getTotalGuestsCount).sum();
        double avgParty = totalReservations > 0 ? (double) totalGuests / totalReservations : 0.0;

        long waitEntries = waitList.stream().mapToLong(WaitingListDailyMetricsEntity::getEntriesCreatedCount).sum();
        long waitConversions = waitList.stream().mapToLong(WaitingListDailyMetricsEntity::getOffersAcceptedCount).sum();
        double convRate = waitEntries > 0 ? ((double) waitConversions / waitEntries) * 100.0 : 0.0;

        double cancellationRate = totalReservations > 0 ? ((double) cancelled / totalReservations) * 100.0 : 0.0;

        Map<String, Long> cancellationsByCategory = new LinkedHashMap<>();
        cancellationsByCategory.put("CUSTOMER_REQUEST", resList.stream().mapToLong(ReservationDailyMetricsEntity::getCancelledCustomerRequestCount).sum());
        cancellationsByCategory.put("NO_SHOW_LATE_CANCEL", resList.stream().mapToLong(ReservationDailyMetricsEntity::getCancelledNoShowCount).sum());
        cancellationsByCategory.put("RESTAURANT_INITIATED", resList.stream().mapToLong(ReservationDailyMetricsEntity::getCancelledRestaurantInitiatedCount).sum());

        Map<String, Long> partySizeDistribution = new LinkedHashMap<>();
        partySizeDistribution.put("1", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize1Count).sum());
        partySizeDistribution.put("2", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize2Count).sum());
        partySizeDistribution.put("3", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize3Count).sum());
        partySizeDistribution.put("4", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize4Count).sum());
        partySizeDistribution.put("5", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize5Count).sum());
        partySizeDistribution.put("6", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize6Count).sum());
        partySizeDistribution.put("7+", resList.stream().mapToLong(ReservationDailyMetricsEntity::getPartySize7PlusCount).sum());

        return new AnalyticsSummary(
                totalReservations,
                completed,
                cancelled,
                noShows,
                avgParty,
                waitEntries,
                waitConversions,
                convRate,
                cancellationRate,
                cancellationsByCategory,
                partySizeDistribution
        );
    }

    private void incrementCounter(String name, String... tags) {
        if (meterRegistry != null) {
            try {
                Counter.builder(name)
                        .tags(tags)
                        .register(meterRegistry)
                        .increment();
            } catch (Exception ignored) {}
        }
    }

    private String mapCancellationCategory(String reason) {
        if (reason == null) {
            return "CUSTOMER_REQUEST";
        }
        String normalized = reason.toUpperCase();
        if (normalized.contains("NO_SHOW") || normalized.contains("LATE") || normalized.contains("TIMEOUT")) {
            return "NO_SHOW_LATE_CANCEL";
        } else if (normalized.contains("RESTAURANT") || normalized.contains("ADMIN") || normalized.contains("KITCHEN") || normalized.contains("CLOSURE")) {
            return "RESTAURANT_INITIATED";
        }
        return "CUSTOMER_REQUEST";
    }

    public record AnalyticsSummary(
            long totalReservations,
            long completedReservations,
            long cancelledReservations,
            long noShows,
            double averagePartySize,
            long waitingListEntries,
            long waitingListConversions,
            double waitingListConversionRate,
            double cancellationRatePercentage,
            Map<String, Long> cancellationsByCategory,
            Map<String, Long> partySizeDistribution
    ) {
        public AnalyticsSummary(long totalReservations, long completedReservations, long cancelledReservations,
                                long noShows, double averagePartySize, long waitingListEntries,
                                long waitingListConversions, double waitingListConversionRate) {
            this(
                    totalReservations,
                    completedReservations,
                    cancelledReservations,
                    noShows,
                    averagePartySize,
                    waitingListEntries,
                    waitingListConversions,
                    waitingListConversionRate,
                    totalReservations > 0 ? ((double) cancelledReservations / totalReservations) * 100.0 : 0.0,
                    Map.of("CUSTOMER_REQUEST", cancelledReservations, "NO_SHOW_LATE_CANCEL", noShows, "RESTAURANT_INITIATED", 0L),
                    Map.of("1", 0L, "2", totalReservations, "3", 0L, "4", 0L, "5", 0L, "6", 0L, "7+", 0L)
            );
        }
    }
}
