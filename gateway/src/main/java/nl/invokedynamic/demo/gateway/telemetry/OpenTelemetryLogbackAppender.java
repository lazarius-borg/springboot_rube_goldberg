package nl.invokedynamic.demo.gateway.telemetry;

import io.opentelemetry.sdk.logs.SdkLoggerProvider;

/**
 * Gateway-specific OpenTelemetry Logback Appender bridging ILoggingEvents to OTLP.
 */
public class OpenTelemetryLogbackAppender extends nl.invokedynamic.demo.events.telemetry.OpenTelemetryLogbackAppender {

    public OpenTelemetryLogbackAppender(SdkLoggerProvider sdkLoggerProvider, String serviceName) {
        super(sdkLoggerProvider, serviceName);
    }
}
