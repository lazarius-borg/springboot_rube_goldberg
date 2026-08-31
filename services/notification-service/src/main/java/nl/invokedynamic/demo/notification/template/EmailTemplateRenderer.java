package nl.invokedynamic.demo.notification.template;

import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class EmailTemplateRenderer {

    public String renderReservationConfirmed(String customerName, Instant startTime, int partySize) {
        return String.format("""
                Hello %s,
                
                Your reservation for %d guest(s) starting at %s has been CONFIRMED!
                
                Thank you for booking with Spring Boot Rube Goldberg.
                """, customerName, partySize, startTime);
    }

    public String renderReservationCancelled(String reason) {
        return String.format("""
                Your reservation has been CANCELLED.
                Reason: %s
                
                We hope to welcome you another time!
                """, reason != null ? reason : "Customer cancellation");
    }

    public String renderWaitingListOffer(String offerId, Instant startTime, Instant expiresAt) {
        return String.format("""
                Good news! A table has opened up for you at %s.
                
                Offer ID: %s
                Please accept by: %s
                """, startTime, offerId, expiresAt);
    }

    public String renderReminder(Instant startTime) {
        return String.format("""
                Friendly reminder for your upcoming dining reservation today at %s!
                """, startTime);
    }
}
