package nl.invokedynamic.demo.waitinglist.api;

import nl.invokedynamic.demo.waitinglist.domain.WaitingListEntryEntity;
import nl.invokedynamic.demo.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WaitingListControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private WaitingListService waitingListService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WaitingListController(waitingListService)).build();
    }

    @Test
    void shouldJoinWaitingList() throws Exception {
        UUID id = UUID.randomUUID();
        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                id, UUID.randomUUID(), UUID.randomUUID(), "bob@example.com",
                LocalDate.of(2026, 9, 1), LocalTime.of(18, 0), LocalTime.of(21, 0), 4, "WAITING", Instant.now()
        );

        when(waitingListService.joinWaitingList(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(entry);

        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "customerEmail": "bob@example.com",
                      "targetDate": "2026-09-01",
                      "earliestTime": "18:00:00",
                      "latestTime": "21:00:00",
                      "partySize": 4
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.customerEmail").value("bob@example.com"));
    }

    @Test
    void shouldReturnGoneOnExpiredOfferAccept() throws Exception {
        UUID offerId = UUID.randomUUID();
        when(waitingListService.acceptOffer(offerId))
                .thenThrow(new IllegalStateException("Offer has expired"));

        mockMvc.perform(post("/api/v1/waiting-list/offers/" + offerId + "/accept"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("Offer has expired"));
    }
}
