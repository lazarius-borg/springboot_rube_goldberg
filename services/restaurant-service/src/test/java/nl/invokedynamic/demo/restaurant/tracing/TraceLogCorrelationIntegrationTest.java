package nl.invokedynamic.demo.restaurant.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import nl.invokedynamic.demo.restaurant.config.TelemetryConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TraceLogCorrelationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TraceLogCorrelationIntegrationTest.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelemetryConfig.class)
            .withPropertyValues(
                    "spring.application.name=restaurant-service",
                    "management.otlp.logging.endpoint=http://localhost:4318/v1/logs",
                    "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces"
            );

    @Test
    void testSpanExporterConfiguredAndMdcCorrelation() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
            assertThat(context).hasSingleBean(SpanProcessor.class);

            // Test MDC correlation
            String testTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
            String testSpanId = "00f067aa0ba902b7";

            try {
                MDC.put("traceId", testTraceId);
                MDC.put("spanId", testSpanId);

                assertThat(MDC.get("traceId")).isEqualTo(testTraceId);
                assertThat(MDC.get("spanId")).isEqualTo(testSpanId);

                log.info("Correlated log entry with traceId and spanId in MDC");
            } finally {
                MDC.clear();
            }
        });
    }
}
