package nl.invokedynamic.demo.customer.tracing;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetrySanitizationTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySanitizationTest.class);
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("Bearer\\s+([A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*)");

    @Test
    void testAuthorizationHeaderRedactionFromTelemetry() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.sensitive-payload.signature");
        headers.put("Content-Type", "application/json");

        // Simulate telemetry header sanitizer
        Map<String, String> sanitizedAttributes = sanitizeHeadersForTelemetry(headers);

        assertThat(sanitizedAttributes)
                .containsEntry("Content-Type", "application/json")
                .doesNotContainValue("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.sensitive-payload.signature");

        assertThat(sanitizedAttributes.get("Authorization")).isEqualTo("[REDACTED]");
        log.info("Verified that sensitive authorization credentials are redacted from telemetry attributes");
    }

    private Map<String, String> sanitizeHeadersForTelemetry(Map<String, String> headers) {
        Map<String, String> sanitized = new HashMap<>();
        headers.forEach((key, value) -> {
            if ("authorization".equalsIgnoreCase(key) || "proxy-authorization".equalsIgnoreCase(key) || "cookie".equalsIgnoreCase(key)) {
                sanitized.put(key, "[REDACTED]");
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }
}
