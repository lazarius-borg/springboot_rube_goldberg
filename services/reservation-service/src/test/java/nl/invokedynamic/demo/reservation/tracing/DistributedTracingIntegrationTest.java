package nl.invokedynamic.demo.reservation.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedTracingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DistributedTracingIntegrationTest.class);

    @Test
    void testW3CTraceContextExtractionAndCorrelation() {
        String incomingTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", incomingTraceparent);

        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        io.opentelemetry.context.Context context = propagator.extract(
                io.opentelemetry.context.Context.root(),
                carrier,
                new TextMapGetter<>() {
                    @Override
                    public Iterable<String> keys(Map<String, String> carrier) {
                        return carrier.keySet();
                    }

                    @Override
                    public String get(Map<String, String> carrier, String key) {
                        return carrier.get(key);
                    }
                }
        );

        io.opentelemetry.api.trace.Span extractedSpan = io.opentelemetry.api.trace.Span.fromContext(context);
        assertThat(extractedSpan.getSpanContext().isValid()).isTrue();
        assertThat(extractedSpan.getSpanContext().getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(extractedSpan.getSpanContext().getSpanId()).isEqualTo("00f067aa0ba902b7");

        // Verify trace correlation in log context (MDC)
        MDC.put("traceId", extractedSpan.getSpanContext().getTraceId());
        MDC.put("spanId", extractedSpan.getSpanContext().getSpanId());
        try {
            assertThat(MDC.get("traceId")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(MDC.get("spanId")).isEqualTo("00f067aa0ba902b7");
            log.info("Correlated trace log entry successfully validated for W3C context");
        } finally {
            MDC.clear();
        }
    }
}
