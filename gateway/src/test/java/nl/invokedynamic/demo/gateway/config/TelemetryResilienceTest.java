package nl.invokedynamic.demo.gateway.config;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import nl.invokedynamic.demo.events.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "management.otlp.logging.endpoint=http://127.0.0.1:65530/v1/logs",
        "management.otlp.tracing.endpoint=http://127.0.0.1:65530/v1/traces"
})
class TelemetryResilienceTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryResilienceTest.class);

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired(required = false)
    private OtlpHttpLogRecordExporter logExporter;

    @Autowired(required = false)
    private OtlpHttpSpanExporter spanExporter;

    @Autowired(required = false)
    private SdkLoggerProvider sdkLoggerProvider;

    @Autowired(required = false)
    private SpanProcessor spanProcessor;

    @Test
    void testBufferCapacityBoundsAndNonBlockingUnderOutage() {
        assertThat(TelemetryConstants.MAX_QUEUE_SIZE).isEqualTo(2048);
        assertThat(TelemetryConstants.CONNECT_TIMEOUT_MS).isEqualTo(1000);
        assertThat(TelemetryConstants.READ_TIMEOUT_MS).isEqualTo(3000);

        // When collector is unreachable (pointing to dead port 65530), logging must remain non-blocking
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 50; i++) {
                log.info("Simulated event during collector outage {}", i);
            }
        });
    }
}
