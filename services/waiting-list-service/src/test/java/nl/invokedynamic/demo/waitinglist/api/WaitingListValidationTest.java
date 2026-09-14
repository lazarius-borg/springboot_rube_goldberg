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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaitingListValidationTest {

    private MockMvc mockMvc;
    @Mock private WaitingListService waitingListService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WaitingListController(waitingListService))
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptValidWaitingListEntry() throws Exception {
        UUID id = UUID.randomUUID();
        WaitingListEntryEntity entry = new WaitingListEntryEntity(
                id, UUID.randomUUID(), UUID.randomUUID(), "valid@example.com",
                LocalDate.now().plusDays(5), LocalTime.of(18, 0), LocalTime.of(21, 0), 4, "WAITING", Instant.now()
        );
        when(waitingListService.joinWaitingList(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(entry);

        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "restaurantId": "00000000-0000-0000-0000-000000000001",
                        "customerEmail": "valid@example.com",
                        "targetDate": "%s",
                        "earliestTime": "18:00:00",
                        "latestTime": "21:00:00",
                        "partySize": 4
                    }
                """.formatted(LocalDate.now().plusDays(5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void shouldRejectInvalidEmailAndPartySize() throws Exception {
        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "restaurantId": "00000000-0000-0000-0000-000000000001",
                        "customerEmail": "not-an-email",
                        "targetDate": "%s",
                        "earliestTime": "18:00:00",
                        "latestTime": "21:00:00",
                        "partySize": 0
                    }
                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItems("customerEmail", "partySize")));
    }

    @Test
    void shouldRejectPartySizeExceedingMaximum() throws Exception {
        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "restaurantId": "00000000-0000-0000-0000-000000000001",
                        "customerEmail": "valid@example.com",
                        "targetDate": "%s",
                        "earliestTime": "18:00:00",
                        "latestTime": "21:00:00",
                        "partySize": 51
                    }
                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("partySize"));
    }

    @Test
    void shouldRejectPastTargetDate() throws Exception {
        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "restaurantId": "00000000-0000-0000-0000-000000000001",
                        "customerEmail": "valid@example.com",
                        "targetDate": "2020-01-01",
                        "earliestTime": "18:00:00",
                        "latestTime": "21:00:00",
                        "partySize": 2
                    }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("targetDate"));
    }

    @Test
    void shouldRejectEarliestTimeAfterLatestTime() throws Exception {
        mockMvc.perform(post("/api/v1/waiting-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "restaurantId": "00000000-0000-0000-0000-000000000001",
                        "customerEmail": "valid@example.com",
                        "targetDate": "%s",
                        "earliestTime": "21:00:00",
                        "latestTime": "18:00:00",
                        "partySize": 2
                    }
                """.formatted(LocalDate.now().plusDays(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("earliestTime"));
    }
}
