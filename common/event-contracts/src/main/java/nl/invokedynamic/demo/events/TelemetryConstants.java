package nl.invokedynamic.demo.events;

/**
 * Shared constants and configuration defaults for the active OpenTelemetry telemetry pipeline.
 */
public final class TelemetryConstants {

    private TelemetryConstants() {
        // Utility class
    }

    /**
     * Maximum capacity of the in-memory batch queues for logs and spans.
     */
    public static final int MAX_QUEUE_SIZE = 2048;

    /**
     * Interval in milliseconds between scheduled batch exporter flushes.
     */
    public static final int FLUSH_INTERVAL_MS = 1000;

    /**
     * Connection timeout in milliseconds for OTLP HTTP exporter requests.
     */
    public static final int CONNECT_TIMEOUT_MS = 1000;

    /**
     * Read and export timeout in milliseconds for OTLP HTTP exporter requests.
     */
    public static final int READ_TIMEOUT_MS = 3000;

    /**
     * Maximum timeout in milliseconds for flushing telemetry buffers during JVM graceful shutdown.
     */
    public static final int SHUTDOWN_TIMEOUT_MS = 5000;

    /**
     * Path prefix for Actuator health and readiness probes to suppress from routine telemetry.
     */
    public static final String HEALTH_PROBE_PATH_PREFIX = "/actuator/health";

    /**
     * Maximum byte length for log messages before truncation.
     */
    public static final int MAX_LOG_MESSAGE_BYTES = 32 * 1024;

    /**
     * Maximum byte length for exception stack traces before truncation.
     */
    public static final int MAX_EXCEPTION_BYTES = 64 * 1024;
}
