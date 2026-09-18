package nl.invokedynamic.demo.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSseEmitterServiceUnitTest {

    private CustomerSseEmitterService service;

    @BeforeEach
    void setUp() {
        service = new CustomerSseEmitterService();
    }

    @Test
    void testRegisterAndConnectionCount() {
        assertThat(service.getActiveConnectionCount()).isZero();

        String customerId = UUID.randomUUID().toString();
        SseEmitter emitter = service.register(customerId, "test@example.com");

        assertThat(emitter).isNotNull();
        assertThat(service.getActiveConnectionCount()).isEqualTo(1);

        service.removeEmitter(emitter);
        assertThat(service.getActiveConnectionCount()).isZero();
    }

    @Test
    void testSendToCustomer() {
        UUID customerId = UUID.randomUUID();
        SseEmitter emitter = service.register(customerId.toString(), "alice@example.com");

        assertThat(service.getActiveConnectionCount()).isEqualTo(1);

        // Dispatches should execute without error
        service.sendToCustomer(customerId, "RESERVATION_CONFIRMED", Map.of("reservationId", UUID.randomUUID().toString()));
        service.sendToCustomerOrEmail(customerId, "alice@example.com", "WAITING_LIST_OFFER", Map.of("offerId", UUID.randomUUID().toString()));
        service.sendHeartbeatPing();

        assertThat(service.getActiveConnectionCount()).isEqualTo(1);
    }
}
