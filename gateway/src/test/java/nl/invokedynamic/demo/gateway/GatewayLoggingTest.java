package nl.invokedynamic.demo.gateway;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/jwks",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class GatewayLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingTest.class);

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private Environment environment;

    @Test
    void testOtlpLoggingAndTracingPropertiesConfigured() {
        String logEndpoint = environment.getProperty("management.otlp.logging.endpoint");
        String traceEndpoint = environment.getProperty("management.otlp.tracing.endpoint");
        String samplingProbability = environment.getProperty("management.tracing.sampling.probability");

        assertThat(logEndpoint).isNotNull().contains("/v1/logs");
        assertThat(traceEndpoint).isNotNull().contains("/v1/traces");
        assertThat(samplingProbability).isEqualTo("1.0");

        log.info("Gateway structured logging verification event emitted successfully");
    }
}
