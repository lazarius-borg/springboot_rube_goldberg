package nl.invokedynamic.demo.analytics.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    private UUID eventId;
    private String eventType;
    private String consumerGroup;
    private Instant processedAt;

    public ProcessedEventEntity() {}

    public ProcessedEventEntity(UUID eventId, String eventType, String consumerGroup, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.consumerGroup = consumerGroup;
        this.processedAt = processedAt;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getConsumerGroup() { return consumerGroup; }
    public Instant getProcessedAt() { return processedAt; }
}
