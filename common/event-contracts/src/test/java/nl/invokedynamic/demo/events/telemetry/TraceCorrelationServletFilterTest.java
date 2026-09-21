package nl.invokedynamic.demo.events.telemetry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TraceCorrelationServletFilterTest {

    private TraceCorrelationServletFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new TraceCorrelationServletFilter("test-service");
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testExtractsW3cTraceparentAndPropagatesMdc() throws Exception {
        String testTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String testSpanId = "00f067aa0ba902b7";
        String traceparent = "00-" + testTraceId + "-" + testSpanId + "-01";

        when(request.getRequestURI()).thenReturn("/api/v1/restaurants");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("traceparent")).thenReturn(traceparent);

        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo(testTraceId);
            assertThat(MDC.get("spanId")).isNotNull().hasSize(16);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verify(response, times(1)).setHeader("X-Trace-Id", testTraceId);
        // Cleaned up after request
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void testGeneratesNewTraceIdWhenMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/restaurants");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("traceparent")).thenReturn(null);
        when(request.getHeader("X-Trace-Id")).thenReturn(null);

        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isNotNull().hasSize(32);
            assertThat(MDC.get("spanId")).isNotNull().hasSize(16);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verify(response, times(1)).setHeader(eq("X-Trace-Id"), anyString());
    }
}
