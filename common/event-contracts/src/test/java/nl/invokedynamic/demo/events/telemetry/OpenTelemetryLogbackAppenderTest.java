package nl.invokedynamic.demo.events.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OpenTelemetryLogbackAppenderTest {

    private SdkLoggerProvider sdkLoggerProvider;
    private Logger otelLogger;
    private LogRecordBuilder logRecordBuilder;
    private OpenTelemetryLogbackAppender appender;

    @BeforeEach
    void setUp() {
        sdkLoggerProvider = mock(SdkLoggerProvider.class);
        io.opentelemetry.api.logs.LoggerBuilder loggerBuilder = mock(io.opentelemetry.api.logs.LoggerBuilder.class);
        otelLogger = mock(Logger.class);
        logRecordBuilder = mock(LogRecordBuilder.class, RETURNS_SELF);

        when(sdkLoggerProvider.loggerBuilder(any())).thenReturn(loggerBuilder);
        when(loggerBuilder.setInstrumentationVersion(any())).thenReturn(loggerBuilder);
        when(loggerBuilder.build()).thenReturn(otelLogger);
        when(otelLogger.logRecordBuilder()).thenReturn(logRecordBuilder);

        appender = new OpenTelemetryLogbackAppender(sdkLoggerProvider, "test-service");
        appender.start();
    }

    @Test
    void testExtractsMdcTraceIdAndSpanId() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.INFO);
        when(event.getFormattedMessage()).thenReturn("Test message");
        when(event.getLoggerName()).thenReturn("nl.invokedynamic.Test");
        when(event.getThreadName()).thenReturn("main");
        when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());

        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";
        when(event.getMDCPropertyMap()).thenReturn(Map.of("traceId", traceId, "spanId", spanId));

        appender.append(event);

        verify(logRecordBuilder).setAttribute(eq(AttributeKey.stringKey("traceId")), eq(traceId));
        verify(logRecordBuilder).setAttribute(eq(AttributeKey.stringKey("spanId")), eq(spanId));
        verify(logRecordBuilder).emit();
    }

    @Test
    void testSuppressesRoutineKafkaPollNoiseAtDebug() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.DEBUG);
        when(event.getFormattedMessage()).thenReturn("Commit list: null");
        when(event.getLoggerName()).thenReturn("org.springframework.kafka.listener.KafkaMessageListenerContainer");

        appender.append(event);

        verify(logRecordBuilder, never()).emit();
    }

    @Test
    void testEmitsApplicationDebugLogs() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.DEBUG);
        when(event.getFormattedMessage()).thenReturn("Application debug details");
        when(event.getLoggerName()).thenReturn("nl.invokedynamic.demo.restaurant.service.RestaurantService");
        when(event.getThreadName()).thenReturn("main");
        when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());

        appender.append(event);

        verify(logRecordBuilder).emit();
    }
}
