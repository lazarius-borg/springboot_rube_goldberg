package nl.invokedynamic.demo.waitinglist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.waitinglist.client.TableInventoryClient;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListEntryEntity;
import nl.invokedynamic.demo.waitinglist.domain.WaitingListOfferEntity;
import nl.invokedynamic.demo.waitinglist.repository.OutboxEventRepository;
import nl.invokedynamic.demo.waitinglist.repository.WaitingListEntryRepository;
import nl.invokedynamic.demo.waitinglist.repository.WaitingListOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitingListFifoLiveMatchingTest {

    @Mock private WaitingListEntryRepository entryRepository;
    @Mock private WaitingListOfferRepository offerRepository;
    @Mock private OutboxEventRepository outboxRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private TableInventoryClient tableInventoryClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private WaitingListService waitingListService;

    @BeforeEach
    void setUp() {
        waitingListService = new WaitingListService(
                entryRepository, offerRepository, outboxRepository, kafkaTemplate,
                objectMapper, tableInventoryClient
        );
    }

    @Test
    void shouldMatchLargerQueuedCandidateWhenReleasedTableCapacityAllowsItInFifoOrder() {
        UUID restId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        LocalDate targetDate = LocalDate.of(2026, 9, 1);
        Instant cancelledStart = Instant.parse("2026-09-01T19:00:00Z");

        // Queued entry 1: Party size 4, entered queue earlier
        WaitingListEntryEntity candidate1 = new WaitingListEntryEntity(
                UUID.randomUUID(), restId, UUID.randomUUID(), "party4@example.com",
                targetDate, LocalTime.of(18, 0), LocalTime.of(20, 0),
                4, "WAITING", Instant.parse("2026-09-01T10:00:00Z")
        );

        // Queued entry 2: Party size 2, entered queue later
        WaitingListEntryEntity candidate2 = new WaitingListEntryEntity(
                UUID.randomUUID(), restId, UUID.randomUUID(), "party2@example.com",
                targetDate, LocalTime.of(18, 0), LocalTime.of(20, 0),
                2, "WAITING", Instant.parse("2026-09-01T11:00:00Z")
        );

        when(entryRepository.findByRestaurantIdAndTargetDateAndStatusOrderByCreatedAtAsc(restId, targetDate, "WAITING"))
                .thenReturn(List.of(candidate1, candidate2));

        // The cancelled reservation was for party of 2, but the physical table capacity released was 4!
        when(tableInventoryClient.getReleasedCapacity(restId, List.of(tableId))).thenReturn(4);

        waitingListService.processCancellationOpening(restId, cancelledStart, 2, List.of(tableId));

        // Candidate 1 (party of 4) should be offered the table
        assertThat(candidate1.getStatus()).isEqualTo("OFFERED");
        assertThat(candidate2.getStatus()).isEqualTo("WAITING");

        ArgumentCaptor<WaitingListOfferEntity> offerCaptor = ArgumentCaptor.forClass(WaitingListOfferEntity.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getWaitingListEntryId()).isEqualTo(candidate1.getId());
    }
}
