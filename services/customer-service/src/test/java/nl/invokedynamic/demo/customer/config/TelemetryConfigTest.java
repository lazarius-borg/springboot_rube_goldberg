package nl.invokedynamic.demo.customer.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryConfigTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConfigTest.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelemetryConfig.class)
            .withPropertyValues(
                    "spring.application.name=customer-service",
                    "management.otlp.logging.endpoint=http://localhost:4318/v1/logs",
                    "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces"
            );

    @Test
    void testCustomerTelemetryLoggingConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OtlpHttpLogRecordExporter.class);
            assertThat(context).hasSingleBean(SdkLoggerProvider.class);

            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            Appender<?> appender = rootLogger.getAppender("OTEL_APPENDER");
            assertThat(appender).isNotNull();
            assertThat(appender.isStarted()).isTrue();

            // Emit logs across severities including DEBUG
            log.debug("Customer service debug log test");
            log.info("Customer service info log test");
            log.warn("Customer service warn log test");
            log.error("Customer service error log test");
        });
    }
}
