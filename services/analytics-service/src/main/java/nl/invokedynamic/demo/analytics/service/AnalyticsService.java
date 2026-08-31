package nl.invokedynamic.demo.analytics.service;

import nl.invokedynamic.demo.analytics.domain.ReservationDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.domain.WaitingListDailyMetricsEntity;
import nl.invokedynamic.demo.analytics.repository.ReservationDailyMetricsRepository;
import nl.invokedynamic.demo.analytics.repository.WaitingListDailyMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final ReservationDailyMetricsRepository resMetricsRepository;
    private final WaitingListDailyMetricsRepository waitMetricsRepository;

    public AnalyticsService(ReservationDailyMetricsRepository resMetricsRepository,
                            WaitingListDailyMetricsRepository waitMetricsRepository) {
        this.resMetricsRepository = resMetricsRepository;
        this.waitMetricsRepository = waitMetricsRepository;
    }

    @Transactional
    public void recordReservationCreated(UUID restaurantId, LocalDate date, int partySize) {
        ReservationDailyMetricsEntity entity = resMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new ReservationDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementCreated(partySize);
        resMetricsRepository.save(entity);
    }

    @Transactional
    public void recordReservationCancelled(UUID restaurantId, LocalDate date) {
        ReservationDailyMetricsEntity entity = resMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new ReservationDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementCancelled();
        resMetricsRepository.save(entity);
    }

    @Transactional
    public void recordWaitingListOfferAccepted(UUID restaurantId, LocalDate date) {
        WaitingListDailyMetricsEntity entity = waitMetricsRepository.findByRestaurantIdAndMetricDate(restaurantId, date)
                .orElseGet(() -> new WaitingListDailyMetricsEntity(UUID.randomUUID(), restaurantId, date));
        entity.incrementOffersAccepted();
        waitMetricsRepository.save(entity);
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

        return new AnalyticsSummary(totalReservations, completed, cancelled, noShows, avgParty, waitEntries, waitConversions, convRate);
    }

    public record AnalyticsSummary(long totalReservations, long completedReservations, long cancelledReservations,
                                  long noShows, double averagePartySize, long waitingListEntries,
                                  long waitingListConversions, double waitingListConversionRate) {}
}
