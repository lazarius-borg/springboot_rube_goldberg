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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaitingListPartySizeWebMvcTest {

    private MockMvc mockMvc;
    @Mock private WaitingListService waitingListService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WaitingListController(waitingListService))
                .build();
    }

    @Test
    void shouldReturnWaitingListEntriesWithPartySizeSerialized() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID restId = UUID.randomUUID();

        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                entryId, restId, customerId, "alice@example.com",
                LocalDate.of(2026, 9, 1), LocalTime.of(18, 0), LocalTime.of(20, 0),
                6, "WAITING", Instant.now()
        );

        when(waitingListService.getWaitingListByCustomer(eq(customerId)))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/waiting-list")
                .param("customerId", customerId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$[0].partySize").value(6))
                .andExpect(jsonPath("$[0].status").value("WAITING"));
    }
}
