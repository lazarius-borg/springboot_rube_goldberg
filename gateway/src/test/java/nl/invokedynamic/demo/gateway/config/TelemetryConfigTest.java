package nl.invokedynamic.demo.gateway.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "management.otlp.logging.endpoint=http://localhost:4318/v1/logs"
})
class TelemetryConfigTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConfigTest.class);

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired(required = false)
    private OtlpHttpLogRecordExporter logExporter;

    @Autowired(required = false)
    private SdkLoggerProvider sdkLoggerProvider;

    @Test
    void testTelemetryLoggingBeansAndAppenderConfigured() {
        assertThat(logExporter).isNotNull();
        assertThat(sdkLoggerProvider).isNotNull();

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        Appender<?> otelAppender = rootLogger.getAppender("OTEL_APPENDER");
        assertThat(otelAppender).isNotNull();
        assertThat(otelAppender.isStarted()).isTrue();

        // Verify dual output: Console appender is still present
        boolean hasConsoleAppender = false;
        var it = rootLogger.iteratorForAppenders();
        while (it.hasNext()) {
            Appender<?> app = it.next();
            if (app.getName() != null && app.getName().toLowerCase().contains("console")) {
                hasConsoleAppender = true;
                break;
            }
        }
        assertThat(hasConsoleAppender).as("Console appender must be retained for dual output").isTrue();

        // Verify emitting logs at various severities succeeds without error
        log.debug("Verification debug event");
        log.info("Verification info event");
        log.warn("Verification warning event");
        log.error("Verification error event", new RuntimeException("Simulated error for test"));
    }
}
