package nl.invokedynamic.demo.notification.mail;

import nl.invokedynamic.demo.notification.domain.NotificationLogEntity;
import nl.invokedynamic.demo.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailpitEmailSenderTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private NotificationLogRepository logRepository;

    private MailpitEmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender = new MailpitEmailSender(javaMailSender, logRepository);
    }

    @Test
    void shouldSendEmailAndLogSuccess() {
        UUID custId = UUID.randomUUID();
        emailSender.sendEmail(custId, "alice@example.com", "TEST", "Subject", "Body content");

        verify(javaMailSender).send(any(SimpleMailMessage.class));
        verify(logRepository, atLeastOnce()).save(any(NotificationLogEntity.class));
    }

    @Test
    void shouldHandleMailExceptionGracefully() {
        doThrow(new RuntimeException("Mail server down")).when(javaMailSender).send(any(SimpleMailMessage.class));

        UUID custId = UUID.randomUUID();
        emailSender.sendEmail(custId, "alice@example.com", "TEST", "Subject", "Body");

        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logRepository, atLeast(2)).save(captor.capture());
        NotificationLogEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
    }
}
