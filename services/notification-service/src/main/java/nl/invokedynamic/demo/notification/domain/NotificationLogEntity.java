package nl.invokedynamic.demo.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
public class NotificationLogEntity {

    @Id
    private UUID id;
    private UUID customerId;
    private String recipientEmail;
    private String notificationType;
    private String subject;
    @Column(columnDefinition = "TEXT")
    private String content;
    private String status;
    private int retryCount = 0;
    private String errorDetail;
    private Instant createdAt;
    private Instant sentAt;

    public NotificationLogEntity() {}

    public NotificationLogEntity(UUID id, UUID customerId, String recipientEmail, String notificationType,
                                 String subject, String content, String status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.recipientEmail = recipientEmail;
        this.notificationType = notificationType;
        this.subject = subject;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getNotificationType() { return notificationType; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
}
