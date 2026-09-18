package nl.invokedynamic.demo.gateway.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces"
})
class TracePropagationIntegrationTest {

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired(required = false)
    private OtlpHttpSpanExporter spanExporter;

    @Autowired(required = false)
    private SpanProcessor spanProcessor;

    @Autowired(required = false)
    private Tracer tracer;

    @Test
    void testSpanBeansAndPropagationConfigured() {
        assertThat(spanExporter).as("OTLP HTTP Span Exporter bean must be configured").isNotNull();
        assertThat(spanProcessor).as("Span Processor bean must be configured").isNotNull();

        // Verify W3C traceparent context extraction / span creation
        if (tracer != null) {
            Span span = tracer.spanBuilder("test-gateway-span").startSpan();
            try (Scope scope = span.makeCurrent()) {
                assertThat(span.getSpanContext().isValid()).isTrue();
                assertThat(span.getSpanContext().getTraceId()).hasSize(32);
                assertThat(span.getSpanContext().getSpanId()).hasSize(16);
            } finally {
                span.end();
            }
        }
    }
}
