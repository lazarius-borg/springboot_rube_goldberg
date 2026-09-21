package nl.invokedynamic.demo.events.telemetry;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.invokedynamic.demo.events.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter providing distributed trace context extraction and MDC correlation
 * across all Spring MVC microservices.
 */
public class TraceCorrelationServletFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceCorrelationServletFilter.class);
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile("^00-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-[0-9a-fA-F]{2}$");
    private static final String ALREADY_APPLIED_ATTR = "TRACE_CORRELATION_FILTER_APPLIED";

    private final String serviceName;

    public TraceCorrelationServletFilter(String serviceName) {
        this.serviceName = serviceName != null ? serviceName : "service";
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (httpRequest.getAttribute(ALREADY_APPLIED_ATTR) != null) {
            chain.doFilter(request, response);
            return;
        }
        httpRequest.setAttribute(ALREADY_APPLIED_ATTR, Boolean.TRUE);

        String uri = httpRequest.getRequestURI();
        boolean isHealthProbe = uri != null && (uri.startsWith(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX)
                || uri.contains(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX));

        String traceId = null;
        String spanId = null;

        String traceparent = httpRequest.getHeader("traceparent");
        if (traceparent != null) {
            Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim());
            if (matcher.matches()) {
                traceId = matcher.group(1).toLowerCase();
            }
        }

        if (traceId == null) {
            String xTraceId = httpRequest.getHeader("X-Trace-Id");
            if (xTraceId != null && xTraceId.length() == 32) {
                traceId = xTraceId.toLowerCase();
            }
        }

        if (traceId == null) {
            traceId = generateHex(32);
        }

        spanId = generateHex(16);

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        httpResponse.setHeader("X-Trace-Id", traceId);

        long startNs = System.nanoTime();
        if (!isHealthProbe) {
            log.info("{} request started: {} {} [traceId={}]", serviceName, httpRequest.getMethod(), uri, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            if (!isHealthProbe) {
                long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                MDC.put("traceId", traceId);
                MDC.put("spanId", spanId);
                log.info("{} request completed: {} {} -> {} in {} ms [traceId={}]",
                        serviceName, httpRequest.getMethod(), uri, httpResponse.getStatus(), durationMs, traceId);
            }
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    private static String generateHex(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.substring(0, length);
    }
}
