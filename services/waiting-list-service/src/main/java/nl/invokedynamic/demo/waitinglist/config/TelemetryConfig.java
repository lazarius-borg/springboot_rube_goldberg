package nl.invokedynamic.demo.waitinglist.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.annotation.PreDestroy;
import nl.invokedynamic.demo.events.TelemetryConstants;
import nl.invokedynamic.demo.events.telemetry.HealthProbeSpanProcessor;
import nl.invokedynamic.demo.events.telemetry.OpenTelemetryLogbackAppender;
import nl.invokedynamic.demo.events.telemetry.TraceCorrelationServletFilter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

/**
 * Active OpenTelemetry logging and distributed tracing configuration for waiting-list-service.
 */
@Configuration
public class TelemetryConfig implements SmartInitializingSingleton {

    private final String serviceName;
    private final String logsEndpoint;
    private final String tracesEndpoint;

    private SdkLoggerProvider sdkLoggerProvider;
    private OpenTelemetryLogbackAppender appender;

    public TelemetryConfig(
            @Value("${spring.application.name:waiting-list-service}") String serviceName,
            @Value("${management.otlp.logging.endpoint:http://localhost:4318/v1/logs}") String logsEndpoint,
            @Value("${management.otlp.tracing.endpoint:http://localhost:4318/v1/traces}") String tracesEndpoint) {
        this.serviceName = serviceName;
        this.logsEndpoint = logsEndpoint;
        this.tracesEndpoint = tracesEndpoint;
    }

    @Bean
    public OtlpHttpLogRecordExporter otlpHttpLogRecordExporter() {
        return OtlpHttpLogRecordExporter.builder()
                .setEndpoint(logsEndpoint)
                .setConnectTimeout(Duration.ofMillis(TelemetryConstants.CONNECT_TIMEOUT_MS))
                .setTimeout(Duration.ofMillis(TelemetryConstants.READ_TIMEOUT_MS))
                .build();
    }

    @Bean
    public SdkLoggerProvider sdkLoggerProvider(OtlpHttpLogRecordExporter logExporter) {
        Resource resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), serviceName)
                .build();

        BatchLogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(logExporter)
                .setMaxQueueSize(TelemetryConstants.MAX_QUEUE_SIZE)
                .setMaxExportBatchSize(512)
                .setScheduleDelay(Duration.ofMillis(TelemetryConstants.FLUSH_INTERVAL_MS))
                .setExporterTimeout(Duration.ofMillis(TelemetryConstants.READ_TIMEOUT_MS))
                .build();

        this.sdkLoggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(batchProcessor)
                .build();

        return this.sdkLoggerProvider;
    }

    @Bean
    public OtlpHttpSpanExporter otlpHttpSpanExporter() {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(tracesEndpoint)
                .setConnectTimeout(Duration.ofMillis(TelemetryConstants.CONNECT_TIMEOUT_MS))
                .setTimeout(Duration.ofMillis(TelemetryConstants.READ_TIMEOUT_MS))
                .build();
    }

    @Bean
    public SpanProcessor otelSpanProcessor(OtlpHttpSpanExporter spanExporter) {
        BatchSpanProcessor batchProcessor = BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(TelemetryConstants.MAX_QUEUE_SIZE)
                .setMaxExportBatchSize(512)
                .setScheduleDelay(Duration.ofMillis(TelemetryConstants.FLUSH_INTERVAL_MS))
                .setExporterTimeout(Duration.ofMillis(TelemetryConstants.READ_TIMEOUT_MS))
                .build();

        return new HealthProbeSpanProcessor(batchProcessor);
    }

    @Bean
    public FilterRegistrationBean<TraceCorrelationServletFilter> traceCorrelationServletFilter() {
        FilterRegistrationBean<TraceCorrelationServletFilter> registration =
                new FilterRegistrationBean<>(new TraceCorrelationServletFilter(serviceName));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (sdkLoggerProvider != null) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

            if (rootLogger.getAppender("OTEL_APPENDER") == null) {
                this.appender = new OpenTelemetryLogbackAppender(sdkLoggerProvider, serviceName);
                this.appender.setContext(context);
                this.appender.setName("OTEL_APPENDER");
                this.appender.start();
                rootLogger.addAppender(this.appender);
            }

            if (rootLogger.getLevel() == null || rootLogger.getLevel().toInt() > Level.DEBUG.toInt()) {
                rootLogger.setLevel(Level.DEBUG);
            }

            // Dampen routine library polling noise so OTLP and logs are not inundated
            context.getLogger("org.apache.kafka").setLevel(Level.INFO);
            context.getLogger("org.springframework.kafka").setLevel(Level.INFO);
            context.getLogger("okhttp3").setLevel(Level.INFO);
            context.getLogger("org.hibernate").setLevel(Level.INFO);
            context.getLogger("org.springframework.orm").setLevel(Level.INFO);
            context.getLogger("org.springframework.transaction").setLevel(Level.INFO);
            context.getLogger("org.postgresql").setLevel(Level.INFO);
            context.getLogger("io.netty").setLevel(Level.INFO);
            context.getLogger("org.apache.catalina").setLevel(Level.INFO);
            context.getLogger("org.apache.tomcat").setLevel(Level.INFO);
            context.getLogger("org.apache.coyote").setLevel(Level.INFO);
            context.getLogger("com.zaxxer.hikari").setLevel(Level.INFO);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (appender != null) {
            appender.stop();
        }
        if (sdkLoggerProvider != null) {
            sdkLoggerProvider.close();
        }
    }
}
