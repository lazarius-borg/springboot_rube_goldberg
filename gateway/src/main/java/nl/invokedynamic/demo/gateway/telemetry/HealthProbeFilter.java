package nl.invokedynamic.demo.gateway.telemetry;

import nl.invokedynamic.demo.events.TelemetryConstants;

import java.util.function.Predicate;

/**
 * Filter predicate to detect routine infrastructure health checks.
 */
public class HealthProbeFilter implements Predicate<String> {

    @Override
    public boolean test(String pathOrUri) {
        if (pathOrUri == null) {
            return false;
        }
        return pathOrUri.startsWith(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX)
                || pathOrUri.contains(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX);
    }
}
