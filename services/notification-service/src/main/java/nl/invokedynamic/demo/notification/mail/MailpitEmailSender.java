package nl.invokedynamic.demo.notification.mail;

import nl.invokedynamic.demo.notification.domain.NotificationLogEntity;
import nl.invokedynamic.demo.notification.repository.NotificationLogRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MailpitEmailSender {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;

    public MailpitEmailSender(JavaMailSender mailSender, NotificationLogRepository logRepository) {
        this.mailSender = mailSender;
        this.logRepository = logRepository;
    }

    public void sendEmail(UUID customerId, String recipient, String type, String subject, String content) {
        NotificationLogEntity log = new NotificationLogEntity(
                UUID.randomUUID(), customerId, recipient, type, subject, content, "PENDING", Instant.now()
        );
        logRepository.save(log);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom("reservations@rubegoldberg.demo");
            msg.setTo(recipient);
            msg.setSubject(subject);
            msg.setText(content);
            mailSender.send(msg);

            log.setStatus("SENT");
            log.setSentAt(Instant.now());
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorDetail(e.getMessage());
        }
        logRepository.save(log);
    }
}
