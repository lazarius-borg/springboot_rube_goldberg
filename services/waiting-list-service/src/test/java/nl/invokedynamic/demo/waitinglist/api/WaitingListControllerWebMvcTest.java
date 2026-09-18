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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        LocalDate futureDate = LocalDate.now().plusDays(5);
        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                id, UUID.randomUUID(), UUID.randomUUID(), "bob@example.com",
                futureDate, LocalTime.of(18, 0), LocalTime.of(21, 0), 4, "WAITING", Instant.now()
        );

        when(waitingListService.joinWaitingList(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(entry);

        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "restaurantId": "00000000-0000-0000-0000-000000000001",
                      "customerEmail": "bob@example.com",
                      "targetDate": "%s",
                      "earliestTime": "18:00:00",
                      "latestTime": "21:00:00",
                      "partySize": 4
                    }
                """, futureDate)))
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

    @Test
    void shouldGetWaitingListEntries() throws Exception {
        UUID restId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        LocalDate targetDate = LocalDate.now().plusDays(2);
        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                entryId, restId, UUID.randomUUID(), "manager-check@example.com",
                targetDate, LocalTime.of(19, 0), LocalTime.of(21, 0), 2, "WAITING", Instant.now()
        );

        when(waitingListService.getWaitingList(eq(restId), any(), any()))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/waiting-list")
                .param("restaurantId", restId.toString())
                .param("targetDate", targetDate.toString())
                .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$[0].customerEmail").value("manager-check@example.com"))
                .andExpect(jsonPath("$[0].status").value("WAITING"));
    }
}
