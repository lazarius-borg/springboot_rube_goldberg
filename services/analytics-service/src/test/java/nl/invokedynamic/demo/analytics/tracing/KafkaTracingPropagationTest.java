package nl.invokedynamic.demo.analytics.tracing;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTracingPropagationTest {

    @Test
    void testKafkaHeaderW3CTracePropagation() {
        String expectedTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String expectedSpanId = "00f067aa0ba902b7";
        String traceparentHeaderValue = "00-" + expectedTraceId + "-" + expectedSpanId + "-01";

        Headers kafkaHeaders = new RecordHeaders();
        kafkaHeaders.add(new RecordHeader("traceparent", traceparentHeaderValue.getBytes(StandardCharsets.UTF_8)));

        // Extract using OpenTelemetry W3C TextMapPropagator over Kafka headers
        io.opentelemetry.context.Context extractedContext = W3CTraceContextPropagator.getInstance().extract(
                io.opentelemetry.context.Context.root(),
                kafkaHeaders,
                new TextMapGetter<>() {
                    @Override
                    public Iterable<String> keys(Headers carrier) {
                        List<String> keys = new ArrayList<>();
                        for (Header h : carrier) {
                            keys.add(h.key());
                        }
                        return keys;
                    }

                    @Override
                    public String get(Headers carrier, String key) {
                        Header header = carrier.lastHeader(key);
                        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
                    }
                }
        );

        io.opentelemetry.api.trace.Span extractedSpan = io.opentelemetry.api.trace.Span.fromContext(extractedContext);
        assertThat(extractedSpan.getSpanContext().isValid()).isTrue();
        assertThat(extractedSpan.getSpanContext().getTraceId()).isEqualTo(expectedTraceId);
        assertThat(extractedSpan.getSpanContext().getSpanId()).isEqualTo(expectedSpanId);
    }
}
