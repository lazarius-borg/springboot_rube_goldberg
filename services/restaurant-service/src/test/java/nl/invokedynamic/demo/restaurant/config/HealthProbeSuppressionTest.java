package nl.invokedynamic.demo.restaurant.config;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import nl.invokedynamic.demo.events.telemetry.HealthProbeSpanProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthProbeSuppressionTest {

    @Test
    void testRoutineHealthProbeSpanSuppression() {
        SpanProcessor delegate = mock(SpanProcessor.class);
        HealthProbeSpanProcessor processor = new HealthProbeSpanProcessor(delegate);

        ReadableSpan healthyProbeSpan = mock(ReadableSpan.class);
        when(healthyProbeSpan.getName()).thenReturn("GET /actuator/health");
        SpanData okSpanData = mock(SpanData.class);
        when(okSpanData.getStatus()).thenReturn(StatusData.ok());
        when(healthyProbeSpan.toSpanData()).thenReturn(okSpanData);

        // Invoking onEnd on a routine healthy probe should NOT forward to delegate
        processor.onEnd(healthyProbeSpan);
        verify(delegate, never()).onEnd(any());

        // An error health probe span MUST be forwarded
        ReadableSpan failedProbeSpan = mock(ReadableSpan.class);
        when(failedProbeSpan.getName()).thenReturn("GET /actuator/health");
        SpanData errorSpanData = mock(SpanData.class);
        when(errorSpanData.getStatus()).thenReturn(StatusData.create(StatusCode.ERROR, "Degraded"));
        when(failedProbeSpan.toSpanData()).thenReturn(errorSpanData);

        processor.onEnd(failedProbeSpan);
        verify(delegate).onEnd(failedProbeSpan);

        // Non-health business spans MUST always be forwarded
        ReadableSpan businessSpan = mock(ReadableSpan.class);
        when(businessSpan.getName()).thenReturn("GET /api/v1/restaurants");
        SpanData businessSpanData = mock(SpanData.class);
        when(businessSpanData.getStatus()).thenReturn(StatusData.ok());
        when(businessSpan.toSpanData()).thenReturn(businessSpanData);

        processor.onEnd(businessSpan);
        verify(delegate).onEnd(businessSpan);
    }
}
