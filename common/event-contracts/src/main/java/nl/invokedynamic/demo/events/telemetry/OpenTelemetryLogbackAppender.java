package nl.invokedynamic.demo.events.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import nl.invokedynamic.demo.events.TelemetryConstants;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Logback Appender that bridges ILoggingEvent instances to OpenTelemetry LogRecordBuilder
 * with structured attributes, MDC trace/span correlation, health probe filtering,
 * size truncation, and sensitive credential scrubbing.
 */
public class OpenTelemetryLogbackAppender extends AppenderBase<ILoggingEvent> {

    private static final AttributeKey<String> SERVICE_NAME_KEY = AttributeKey.stringKey("serviceName");
    private static final AttributeKey<String> LOGGER_KEY = AttributeKey.stringKey("logger");
    private static final AttributeKey<String> THREAD_KEY = AttributeKey.stringKey("thread");
    private static final AttributeKey<String> SEVERITY_KEY = AttributeKey.stringKey("severity");
    private static final AttributeKey<String> TRACE_ID_KEY = AttributeKey.stringKey("traceId");
    private static final AttributeKey<String> SPAN_ID_KEY = AttributeKey.stringKey("spanId");
    private static final AttributeKey<String> EXCEPTION_KEY = AttributeKey.stringKey("exception");

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(Bearer\\s+[A-Za-z0-9-_.]+)|(password\\s*[:=]\\s*[^\\s,;]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final SdkLoggerProvider sdkLoggerProvider;
    private final String serviceName;
    private Logger otelLogger;

    public OpenTelemetryLogbackAppender(SdkLoggerProvider sdkLoggerProvider, String serviceName) {
        this.sdkLoggerProvider = sdkLoggerProvider;
        this.serviceName = serviceName != null ? serviceName : "unknown-service";
    }

    @Override
    public void start() {
        if (sdkLoggerProvider != null) {
            this.otelLogger = sdkLoggerProvider.loggerBuilder("nl.invokedynamic.demo.logging")
                    .setInstrumentationVersion("1.0.0")
                    .build();
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || otelLogger == null || !isStarted()) {
            return;
        }

        // Suppress routine health check logs and noisy third-party background pollers
        if (isSuppressed(event)) {
            return;
        }

        try {
            LogRecordBuilder builder = otelLogger.logRecordBuilder();
            builder.setTimestamp(event.getTimeStamp(), TimeUnit.MILLISECONDS);

            Severity severity = mapSeverity(event.getLevel());
            builder.setSeverity(severity);
            builder.setSeverityText(event.getLevel().toString());

            String rawMessage = event.getFormattedMessage();
            String sanitizedMessage = sanitize(rawMessage);
            if (sanitizedMessage != null && sanitizedMessage.length() > TelemetryConstants.MAX_LOG_MESSAGE_BYTES) {
                sanitizedMessage = sanitizedMessage.substring(0, TelemetryConstants.MAX_LOG_MESSAGE_BYTES) + " [TRUNCATED]";
            }
            builder.setBody(sanitizedMessage != null ? sanitizedMessage : "");

            // Standard structured attributes
            builder.setAttribute(SERVICE_NAME_KEY, serviceName);
            builder.setAttribute(LOGGER_KEY, event.getLoggerName() != null ? event.getLoggerName() : "");
            builder.setAttribute(THREAD_KEY, event.getThreadName() != null ? event.getThreadName() : "");
            builder.setAttribute(SEVERITY_KEY, event.getLevel().toString());

            // Exception handling with size truncation
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                String stackTrace = ThrowableProxyUtil.asString(throwableProxy);
                if (stackTrace != null) {
                    if (stackTrace.length() > TelemetryConstants.MAX_EXCEPTION_BYTES) {
                        stackTrace = stackTrace.substring(0, TelemetryConstants.MAX_EXCEPTION_BYTES) + " [TRUNCATED]";
                    }
                    builder.setAttribute(EXCEPTION_KEY, stackTrace);
                }
            }

            // Trace Context correlation from MDC or active Span
            Map<String, String> mdc = event.getMDCPropertyMap();
            String traceId = mdc != null ? mdc.get("traceId") : null;
            String spanId = mdc != null ? mdc.get("spanId") : null;

            if (traceId == null && mdc != null) {
                traceId = mdc.get("trace_id");
                if (traceId == null) {
                    traceId = mdc.get("X-B3-TraceId");
                }
                if (traceId == null && mdc.containsKey("traceparent")) {
                    String tp = mdc.get("traceparent");
                    if (tp != null && tp.length() >= 35 && tp.startsWith("00-")) {
                        String[] parts = tp.split("-");
                        if (parts.length >= 3) {
                            traceId = parts[1];
                            if (spanId == null) {
                                spanId = parts[2];
                            }
                        }
                    }
                }
            }
            if (spanId == null && mdc != null) {
                spanId = mdc.get("span_id");
                if (spanId == null) {
                    spanId = mdc.get("X-B3-SpanId");
                }
            }

            if (traceId == null || spanId == null) {
                Span currentSpan = Span.current();
                if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
                    if (traceId == null) {
                        traceId = currentSpan.getSpanContext().getTraceId();
                    }
                    if (spanId == null) {
                        spanId = currentSpan.getSpanContext().getSpanId();
                    }
                }
            }

            boolean validTraceId = isValidHex(traceId, 32);
            boolean validSpanId = isValidHex(spanId, 16);

            if (validTraceId) {
                builder.setAttribute(TRACE_ID_KEY, traceId);
            }
            if (validSpanId) {
                builder.setAttribute(SPAN_ID_KEY, spanId);
            }

            if (validTraceId && validSpanId) {
                SpanContext spanContext = SpanContext.create(
                        traceId,
                        spanId,
                        TraceFlags.getSampled(),
                        TraceState.getDefault()
                );
                builder.setContext(Context.root().with(Span.wrap(spanContext)));
            }

            builder.emit();
        } catch (Exception ignored) {
            // Drop non-blockingly on exporter errors to safeguard application thread execution
        }
    }

    private boolean isSuppressed(ILoggingEvent event) {
        if (isSuppressedHealthProbe(event)) {
            return true;
        }

        // Suppress routine third-party framework background polls at TRACE/DEBUG
        if (event.getLevel() != null && event.getLevel().toInt() <= Level.DEBUG_INT) {
            String logger = event.getLoggerName();
            if (logger != null) {
                if (logger.startsWith("org.apache.kafka")
                        || logger.startsWith("org.springframework.kafka")
                        || logger.startsWith("okhttp3")
                        || logger.startsWith("org.hibernate")
                        || logger.startsWith("org.springframework.orm")
                        || logger.startsWith("org.springframework.transaction")
                        || logger.startsWith("org.springframework.data")
                        || logger.startsWith("org.postgresql")
                        || logger.startsWith("io.netty")
                        || logger.startsWith("org.apache.catalina")
                        || logger.startsWith("org.apache.tomcat")
                        || logger.startsWith("org.apache.coyote")
                        || logger.startsWith("com.zaxxer.hikari")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSuppressedHealthProbe(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        String logger = event.getLoggerName();
        boolean isHealthEndpoint = (msg != null && msg.contains(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX))
                || (logger != null && logger.contains("Actuator"));
        if (isHealthEndpoint) {
            // Only emit health telemetry on WARN or ERROR (degraded/failure states)
            return event.getLevel().toInt() < Level.WARN.toInt();
        }
        return false;
    }

    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return SENSITIVE_PATTERN.matcher(message).replaceAll("[REDACTED]");
    }

    private static boolean isValidHex(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) {
            return false;
        }
        for (int i = 0; i < expectedLength; i++) {
            char c = value.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    private static Severity mapSeverity(Level level) {
        if (level == null) {
            return Severity.UNDEFINED_SEVERITY_NUMBER;
        }
        return switch (level.toInt()) {
            case Level.TRACE_INT -> Severity.TRACE;
            case Level.DEBUG_INT -> Severity.DEBUG;
            case Level.INFO_INT -> Severity.INFO;
            case Level.WARN_INT -> Severity.WARN;
            case Level.ERROR_INT -> Severity.ERROR;
            default -> Severity.UNDEFINED_SEVERITY_NUMBER;
        };
    }
}
