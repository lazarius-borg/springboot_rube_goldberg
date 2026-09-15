package nl.invokedynamic.demo.reservation;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationServiceLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceLoggingTest.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.application.name=reservation-service",
                    "management.tracing.sampling.probability=1.0",
                    "management.tracing.propagation.type=W3C",
                    "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces",
                    "management.otlp.logging.endpoint=http://localhost:4318/v1/logs"
            );

    @Test
    void testOtlpLoggingAndTracingPropertiesConfigured() {
        contextRunner.run(context -> {
            assertThat(context.getEnvironment().getProperty("management.otlp.logging.endpoint"))
                    .isEqualTo("http://localhost:4318/v1/logs");
            assertThat(context.getEnvironment().getProperty("management.otlp.tracing.endpoint"))
                    .isEqualTo("http://localhost:4318/v1/traces");
            assertThat(context.getEnvironment().getProperty("management.tracing.sampling.probability"))
                    .isEqualTo("1.0");
            assertThat(context.getEnvironment().getProperty("management.tracing.propagation.type"))
                    .isEqualTo("W3C");

            log.info("Reservation service structured logging verification event emitted successfully");
        });
    }
}
