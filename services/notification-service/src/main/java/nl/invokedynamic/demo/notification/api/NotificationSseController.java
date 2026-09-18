package nl.invokedynamic.demo.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import nl.invokedynamic.demo.notification.service.CustomerSseEmitterService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications Stream", description = "Server-Sent Events (SSE) push streaming endpoint")
public class NotificationSseController {

    private final CustomerSseEmitterService emitterService;

    public NotificationSseController(CustomerSseEmitterService emitterService) {
        this.emitterService = emitterService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time customer notifications SSE stream")
    @ApiResponse(responseCode = "200", description = "SSE stream established")
    public SseEmitter subscribeToStream(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "customerId", required = false) UUID customerId) {

        String resolvedCustomerId = customerId != null ? customerId.toString() : null;
        if (resolvedCustomerId == null && jwt != null) {
            String claimCustomerId = jwt.getClaimAsString("customerId");
            resolvedCustomerId = claimCustomerId != null && !claimCustomerId.isBlank()
                    ? claimCustomerId
                    : jwt.getSubject();
        }

        String email = jwt != null ? jwt.getClaimAsString("email") : null;

        return emitterService.register(resolvedCustomerId, email);
    }
}
