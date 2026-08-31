package nl.invokedynamic.demo.notification.scheduler;

import nl.invokedynamic.demo.notification.domain.ReminderScheduleEntity;
import nl.invokedynamic.demo.notification.mail.MailpitEmailSender;
import nl.invokedynamic.demo.notification.repository.ReminderScheduleRepository;
import nl.invokedynamic.demo.notification.template.EmailTemplateRenderer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class ReservationReminderScheduler {

    private final ReminderScheduleRepository reminderRepository;
    private final MailpitEmailSender emailSender;
    private final EmailTemplateRenderer templateRenderer;

    public ReservationReminderScheduler(ReminderScheduleRepository reminderRepository,
                                         MailpitEmailSender emailSender,
                                         EmailTemplateRenderer templateRenderer) {
        this.reminderRepository = reminderRepository;
        this.emailSender = emailSender;
        this.templateRenderer = templateRenderer;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void sendDueReminders() {
        List<ReminderScheduleEntity> due = reminderRepository.findByStatusAndScheduledReminderTimeBefore("SCHEDULED", Instant.now());
        for (ReminderScheduleEntity r : due) {
            r.setStatus("SENT");
            reminderRepository.save(r);
            String body = templateRenderer.renderReminder(r.getScheduledReminderTime());
            emailSender.sendEmail(r.getCustomerId(), r.getCustomerEmail(), "RESERVATION_REMINDER", "Upcoming Reservation Reminder", body);
        }
    }
}
