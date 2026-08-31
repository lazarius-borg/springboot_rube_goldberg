package nl.invokedynamic.demo.notification.template;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void shouldRenderConfirmedTemplate() {
        String result = renderer.renderReservationConfirmed("Alice", Instant.parse("2026-09-01T19:00:00Z"), 4);
        assertThat(result).contains("Alice", "4", "CONFIRMED");
    }
}
