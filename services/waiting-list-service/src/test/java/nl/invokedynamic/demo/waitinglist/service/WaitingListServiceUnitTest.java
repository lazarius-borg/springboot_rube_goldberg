package nl.invokedynamic.demo.waitinglist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListEntryEntity;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListOfferEntity;
import nl.invokedynamic.demo.waitinglist.repository.OutboxEventRepository;
import nl.invokedynamic.demo.waitinglist.repository.WaitingListEntryRepository;
import nl.invokedynamic.demo.waitinglist.repository.WaitingListOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingListServiceUnitTest {

    @Mock private WaitingListEntryRepository entryRepository;
    @Mock private WaitingListOfferRepository offerRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private WaitingListService service;
    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-09-01T12:00:00Z"),
            ZoneOffset.UTC
    );

    @BeforeEach
    void setUp() {
        service = new WaitingListService(entryRepository, offerRepository, outboxRepository, kafkaTemplate, objectMapper, fixedClock);
    }

    @Test
    void shouldJoinWaitingListAndSaveOutbox() {
        UUID restId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();

        WaitingListEntryEntity entry = service.joinWaitingList(
                restId, custId, "bob@example.com", LocalDate.of(2026, 9, 1),
                LocalTime.of(18, 0), LocalTime.of(21, 0), 4
        );

        assertThat(entry).isNotNull();
        assertThat(entry.getStatus()).isEqualTo("WAITING");
        verify(entryRepository).save(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldMatchOldestCandidateOnCancellation() {
        UUID restId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Instant cancelledStart = Instant.parse("2026-09-01T19:00:00Z");

        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                entryId, restId, UUID.randomUUID(), "bob@example.com",
                LocalDate.of(2026, 9, 1), LocalTime.of(18, 0), LocalTime.of(21, 0),
                4, "WAITING", Instant.now().minusSeconds(100)
        );

        when(entryRepository.findByRestaurantIdAndTargetDateAndStatusOrderByCreatedAtAsc(eq(restId), any(), eq("WAITING")))
                .thenReturn(List.of(entry));

        service.processCancellationOpening(restId, cancelledStart, 4, List.of(UUID.randomUUID()));

        assertThat(entry.getStatus()).isEqualTo("OFFERED");
        verify(offerRepository).save(any(WaitingListOfferEntity.class));
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldAcceptPendingOffer() {
        UUID offerId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        WaitingListOfferEntity offer = new WaitingListOfferEntity(
                offerId, entryId, UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1)),
                List.of(UUID.randomUUID()), Instant.now().plus(Duration.ofMinutes(15)),
                "PENDING", Instant.now(), Instant.now()
        );
        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                entryId, UUID.randomUUID(), UUID.randomUUID(), "bob@example.com",
                LocalDate.now(), LocalTime.MIN, LocalTime.MAX, 4, "OFFERED", Instant.now()
        );

        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(entryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        WaitingListOfferEntity accepted = service.acceptOffer(offerId);
        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
        assertThat(entry.getStatus()).isEqualTo("CONVERTED");
        verify(outboxRepository).save(any());
    }

    @Test
    void shouldRejectExpiredOfferAcceptance() {
        UUID offerId = UUID.randomUUID();
        WaitingListOfferEntity offer = new WaitingListOfferEntity(
                offerId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                List.of(), Instant.now().minusSeconds(60), "PENDING", Instant.now(), Instant.now()
        );

        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.acceptOffer(offerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Offer has expired");
    }

    @Test
    void shouldClampEarliestTimeToNowWhenWithinGraceWindowOnSameDay() {
        UUID restId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        // Fixed clock is 2026-09-01T12:00:00Z -> In Europe/Amsterdam (UTC+2 in Sep DST), local time is 14:00:00
        // 13:57:00 is 3 minutes in past -> within 5 minute grace period
        LocalDate today = LocalDate.of(2026, 9, 1);
        LocalTime requestedEarliest = LocalTime.of(13, 57, 0);
        LocalTime latest = LocalTime.of(16, 0, 0);

        WaitingListEntryEntity entry = service.joinWaitingList(
                restId, custId, "alice@example.com", today, requestedEarliest, latest, 2
        );

        assertThat(entry).isNotNull();
        assertThat(entry.getEarliestTime()).isEqualTo(LocalTime.of(14, 0, 0)); // Clamped to now
        assertThat(entry.getLatestTime()).isEqualTo(latest);
    }

    @Test
    void shouldRejectSameDayWhenEarliestTimePastGraceWindow() {
        UUID restId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        // 13:50:00 is 10 minutes in past -> beyond 5 minute grace window
        LocalDate today = LocalDate.of(2026, 9, 1);
        LocalTime requestedEarliest = LocalTime.of(13, 50, 0);
        LocalTime latest = LocalTime.of(16, 0, 0);

        assertThatThrownBy(() -> service.joinWaitingList(
                restId, custId, "alice@example.com", today, requestedEarliest, latest, 2
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Earliest seating time cannot be in the past");
    }

    @Test
    void shouldRejectSameDayWhenLatestTimeInThePast() {
        UUID restId = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        // 13:30:00 and 13:45:00 -> latest time is before 14:00
        LocalDate today = LocalDate.of(2026, 9, 1);
        LocalTime requestedEarliest = LocalTime.of(13, 30, 0);
        LocalTime latest = LocalTime.of(13, 45, 0);

        assertThatThrownBy(() -> service.joinWaitingList(
                restId, custId, "alice@example.com", today, requestedEarliest, latest, 2
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Seating time window has already passed");
    }

    @Test
    void shouldQueryWaitingListWithVariousFilterCombinations() {
        UUID restId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 20);

        // 1. All filters
        service.getWaitingList(restId, date, "WAITING");
        verify(entryRepository).findByRestaurantIdAndTargetDateAndStatusOrderByCreatedAtAsc(restId, date, "WAITING");

        // 2. Date only
        service.getWaitingList(restId, date, null);
        verify(entryRepository).findByRestaurantIdAndTargetDateOrderByCreatedAtAsc(restId, date);

        // 3. Status only
        service.getWaitingList(restId, null, "WAITING");
        verify(entryRepository).findByRestaurantIdAndStatusOrderByCreatedAtAsc(restId, "WAITING");

        // 4. Restaurant ID only
        service.getWaitingList(restId, null, null);
        verify(entryRepository).findByRestaurantIdOrderByCreatedAtAsc(restId);
    }
}
