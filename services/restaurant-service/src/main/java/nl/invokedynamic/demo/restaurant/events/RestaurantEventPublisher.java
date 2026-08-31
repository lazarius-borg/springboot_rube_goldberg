package nl.invokedynamic.demo.restaurant.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.invokedynamic.demo.restaurant.domain.OutboxEventEntity;
import nl.invokedynamic.demo.restaurant.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class RestaurantEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RestaurantEventPublisher(OutboxEventRepository outboxEventRepository,
                                    KafkaTemplate<String, Object> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEventEntity event : pendingEvents) {
            try {
                kafkaTemplate.send("restaurant.events", event.getAggregateId(), event.getPayload());
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                // Log and retry on next run
                break;
            }
        }
    }
}
