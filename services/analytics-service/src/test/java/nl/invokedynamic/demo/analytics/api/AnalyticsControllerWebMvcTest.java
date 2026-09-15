package nl.invokedynamic.demo.analytics.api;

import nl.invokedynamic.demo.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void shouldReturnSummaryWithGranularBreakdowns() throws Exception {
        AnalyticsService.AnalyticsSummary mockSummary = new AnalyticsService.AnalyticsSummary(
                10, 8, 2, 0, 3.5, 5, 3, 60.0, 20.0,
                Map.of("CUSTOMER_REQUEST", 2L, "NO_SHOW_LATE_CANCEL", 0L, "RESTAURANT_INITIATED", 0L),
                Map.of("1", 1L, "2", 5L, "3", 2L, "4", 1L, "5", 0L, "6", 0L, "7+", 1L)
        );

        when(analyticsService.getSummary(any())).thenReturn(mockSummary);

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservations").value(10))
                .andExpect(jsonPath("$.completedReservations").value(8))
                .andExpect(jsonPath("$.cancelledReservations").value(2))
                .andExpect(jsonPath("$.cancellationRatePercentage").value(20.0))
                .andExpect(jsonPath("$.cancellationsByCategory.CUSTOMER_REQUEST").value(2))
                .andExpect(jsonPath("$.partySizeDistribution.2").value(5))
                .andExpect(jsonPath("$.partySizeDistribution['7+']").value(1))
                .andExpect(jsonPath("$.waitingListConversionRate").value(60.0));
    }

    @Test
    void shouldPassRestaurantIdFilterToService() throws Exception {
        UUID restId = UUID.randomUUID();
        AnalyticsService.AnalyticsSummary mockSummary = new AnalyticsService.AnalyticsSummary(
                0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0,
                Map.of("CUSTOMER_REQUEST", 0L, "NO_SHOW_LATE_CANCEL", 0L, "RESTAURANT_INITIATED", 0L),
                Map.of("1", 0L, "2", 0L, "3", 0L, "4", 0L, "5", 0L, "6", 0L, "7+", 0L)
        );

        when(analyticsService.getSummary(eq(restId))).thenReturn(mockSummary);

        mockMvc.perform(get("/api/v1/analytics/summary").param("restaurantId", restId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservations").value(0))
                .andExpect(jsonPath("$.cancellationRatePercentage").value(0.0));
    }
}
