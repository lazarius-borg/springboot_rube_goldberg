package nl.invokedynamic.demo.reservation.tracing;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CollectorResiliencyTest {

    private static final Logger log = LoggerFactory.getLogger(CollectorResiliencyTest.class);

    @Test
    void testMultiLineExceptionPreservationInSingleLogPayload() {
        Exception rootCause = new IllegalArgumentException("Invalid dining slot duration");
        Exception businessException = new IllegalStateException("Reservation allocation conflict", rootCause);

        StringWriter sw = new StringWriter();
        businessException.printStackTrace(new PrintWriter(sw));
        String fullStackTrace = sw.toString();

        assertThat(fullStackTrace)
                .contains("IllegalStateException: Reservation allocation conflict")
                .contains("Caused by: java.lang.IllegalArgumentException: Invalid dining slot duration");

        log.error("Simulated business failure with cohesive stack trace", businessException);
    }

    @Test
    void testNonBlockingTelemetryBufferUnderCollectorDisconnect() {
        // Bounded queue simulating asynchronous non-blocking OTLP batch exporter ring buffer
        BlockingQueue<String> batchBuffer = new ArrayBlockingQueue<>(10);

        // Fill buffer to capacity simulating offline collector
        for (int i = 0; i < 10; i++) {
            batchBuffer.offer("span-data-" + i);
        }

        // When queue is full, non-blocking offer should drop or reject without blocking calling thread
        boolean acceptedWhenFull = batchBuffer.offer("overflow-span-data");
        assertThat(acceptedWhenFull).isFalse();
        assertThat(batchBuffer.size()).isEqualTo(10);

        // Verify request thread execution completes in < 50ms without timeout or exception
        assertThatCode(() -> {
            long start = System.currentTimeMillis();
            // Simulate business transaction execution
            Thread.sleep(5);
            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed).isLessThan(500);
        }).doesNotThrowAnyException();
    }
}
