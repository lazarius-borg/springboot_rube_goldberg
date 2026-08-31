package nl.invokedynamic.demo.analytics.api;

import nl.invokedynamic.demo.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerWebMvcTest {

    private MockMvc mockMvc;
    @Mock private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(analyticsService)).build();
    }

    @Test
    void shouldReturnSummary() throws Exception {
        when(analyticsService.getSummary(any()))
                .thenReturn(new AnalyticsService.AnalyticsSummary(10, 8, 2, 0, 3.5, 5, 3, 60.0));

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservations").value(10))
                .andExpect(jsonPath("$.completedReservations").value(8))
                .andExpect(jsonPath("$.cancelledReservations").value(2))
                .andExpect(jsonPath("$.waitingListConversionRate").value(60.0));
    }
}
