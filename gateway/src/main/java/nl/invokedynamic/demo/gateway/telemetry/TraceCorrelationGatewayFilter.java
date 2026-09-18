package nl.invokedynamic.demo.gateway.telemetry;

import nl.invokedynamic.demo.events.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Cloud Gateway GlobalFilter ensuring all routed requests have W3C traceparent
 * headers propagated downstream and emit correlated log records with traceId and spanId.
 */
@Component
public class TraceCorrelationGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceCorrelationGatewayFilter.class);
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile("^00-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-[0-9a-fA-F]{2}$");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        boolean isHealthProbe = path != null && (path.startsWith(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX)
                || path.contains(TelemetryConstants.HEALTH_PROBE_PATH_PREFIX));

        String incomingTraceparent = request.getHeaders().getFirst("traceparent");
        String traceId = null;
        if (incomingTraceparent != null) {
            Matcher matcher = TRACEPARENT_PATTERN.matcher(incomingTraceparent.trim());
            if (matcher.matches()) {
                traceId = matcher.group(1).toLowerCase();
            }
        }

        if (traceId == null) {
            String xTraceId = request.getHeaders().getFirst("X-Trace-Id");
            if (xTraceId != null && xTraceId.length() == 32) {
                traceId = xTraceId.toLowerCase();
            }
        }

        if (traceId == null) {
            traceId = generateHex(32);
        }

        String spanId = generateHex(16);
        String childSpanId = generateHex(16);
        String outgoingTraceparent = "00-" + traceId + "-" + childSpanId + "-01";

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("traceparent", outgoingTraceparent)
                .header("X-Trace-Id", traceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        mutatedExchange.getResponse().getHeaders().set("X-Trace-Id", traceId);

        long startNs = System.nanoTime();
        final String finalTraceId = traceId;
        final String finalSpanId = spanId;

        if (!isHealthProbe) {
            try {
                MDC.put("traceId", finalTraceId);
                MDC.put("spanId", finalSpanId);
                log.info("Gateway routing {} {} [traceId={}]", request.getMethod(), path, finalTraceId);
            } finally {
                MDC.clear();
            }
        }

        return chain.filter(mutatedExchange)
                .doOnError(err -> {
                    if (!isHealthProbe) {
                        try {
                            MDC.put("traceId", finalTraceId);
                            MDC.put("spanId", finalSpanId);
                            log.error("Gateway error for {} {} [traceId={}]: {}", request.getMethod(), path, finalTraceId, err.getMessage(), err);
                        } finally {
                            MDC.clear();
                        }
                    }
                })
                .doFinally(signalType -> {
                    if (!isHealthProbe) {
                        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                        int statusCode = mutatedExchange.getResponse().getStatusCode() != null
                                ? mutatedExchange.getResponse().getStatusCode().value()
                                : 0;
                        try {
                            MDC.put("traceId", finalTraceId);
                            MDC.put("spanId", finalSpanId);
                            log.info("Gateway completed {} {} -> {} in {} ms [traceId={}]",
                                    request.getMethod(), path, statusCode, durationMs, finalTraceId);
                        } finally {
                            MDC.clear();
                        }
                    }
                });
    }

    private static String generateHex(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.substring(0, length);
    }
}
