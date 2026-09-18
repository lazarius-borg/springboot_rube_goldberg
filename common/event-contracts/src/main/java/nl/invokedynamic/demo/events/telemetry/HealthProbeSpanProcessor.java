package nl.invokedynamic.demo.events.telemetry;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import nl.invokedynamic.demo.events.TelemetryConstants;

/**
 * OpenTelemetry SpanProcessor decorator that suppresses routine synthetic health and readiness
 * probes targeting /actuator/health/** from being exported, allowing spans only when an error occurs.
 */
public class HealthProbeSpanProcessor implements SpanProcessor {

    private final SpanProcessor delegate;

    public HealthProbeSpanProcessor(SpanProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        if (delegate != null) {
            delegate.onStart(parentContext, span);
        }
    }

    @Override
    public boolean isStartRequired() {
        return delegate != null && delegate.isStartRequired();
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (span == null || delegate == null) {
            return;
        }

        String spanName = span.getName();
        boolean isHealthProbe = spanName != null && spanName.contains(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX);

        // If routine health probe and not in an ERROR state, drop the span
        if (isHealthProbe && span.toSpanData().getStatus().getStatusCode() != StatusCode.ERROR) {
            return;
        }

        delegate.onEnd(span);
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate != null ? delegate.shutdown() : CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return delegate != null ? delegate.forceFlush() : CompletableResultCode.ofSuccess();
    }
}
