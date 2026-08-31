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

    @BeforeEach
    void setUp() {
        service = new WaitingListService(entryRepository, offerRepository, outboxRepository, kafkaTemplate, objectMapper);
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
}
