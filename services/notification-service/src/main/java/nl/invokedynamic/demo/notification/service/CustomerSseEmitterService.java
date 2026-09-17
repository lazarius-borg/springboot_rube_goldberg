package nl.invokedynamic.demo.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CustomerSseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(CustomerSseEmitterService.class);
    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    // Maps customer identifier (UUID string, subject ID, or email) to active SseEmitters
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> customerEmitters = new ConcurrentHashMap<>();

    public SseEmitter register(String customerId, String email) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        Set<String> keys = new HashSet<>();
        if (customerId != null && !customerId.isBlank()) {
            keys.add(customerId.toLowerCase());
        }
        if (email != null && !email.isBlank()) {
            keys.add(email.toLowerCase());
        }

        for (String key : keys) {
            customerEmitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        }

        Runnable cleanup = () -> {
            for (String key : keys) {
                CopyOnWriteArrayList<SseEmitter> list = customerEmitters.get(key);
                if (list != null) {
                    list.remove(emitter);
                    if (list.isEmpty()) {
                        customerEmitters.remove(key, list);
                    }
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial connected event
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "CONNECTED");
            payload.put("customerId", customerId != null ? customerId : "");
            emitter.send(SseEmitter.event().name("connected").data(payload));
        } catch (IOException e) {
            log.warn("Failed to send initial connected event to customer {}: {}", customerId, e.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    void removeEmitter(SseEmitter emitter) {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : customerEmitters.entrySet()) {
            entry.getValue().remove(emitter);
            if (entry.getValue().isEmpty()) {
                customerEmitters.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public void sendToCustomer(UUID customerId, String eventName, Object data) {
        if (customerId != null) {
            sendToKey(customerId.toString().toLowerCase(), eventName, data);
        }
    }

    public void sendToCustomer(String customerKey, String eventName, Object data) {
        if (customerKey != null && !customerKey.isBlank()) {
            sendToKey(customerKey.toLowerCase(), eventName, data);
        }
    }

    public void sendToCustomerOrEmail(UUID customerId, String email, String eventName, Object data) {
        Set<SseEmitter> sentEmitters = Collections.newSetFromMap(new IdentityHashMap<>());
        if (customerId != null) {
            sendToKeyDeduplicated(customerId.toString().toLowerCase(), eventName, data, sentEmitters);
        }
        if (email != null && !email.isBlank()) {
            sendToKeyDeduplicated(email.toLowerCase(), eventName, data, sentEmitters);
        }
    }

    private void sendToKey(String key, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = customerEmitters.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.debug("Error sending {} to customer key {}: {}", eventName, key, e.getMessage());
                deadEmitters.add(emitter);
            }
        }
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    private void sendToKeyDeduplicated(String key, String eventName, Object data, Set<SseEmitter> alreadySent) {
        CopyOnWriteArrayList<SseEmitter> emitters = customerEmitters.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            if (alreadySent.add(emitter)) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (Exception e) {
                    log.debug("Error sending {} to customer key {}: {}", eventName, key, e.getMessage());
                    deadEmitters.add(emitter);
                }
            }
        }
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeatPing() {
        if (customerEmitters.isEmpty()) {
            return;
        }

        Set<SseEmitter> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : customerEmitters.entrySet()) {
            CopyOnWriteArrayList<SseEmitter> list = entry.getValue();
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : list) {
                if (checked.add(emitter)) {
                    try {
                        emitter.send(SseEmitter.event().name("ping").data(""));
                    } catch (Exception e) {
                        dead.add(emitter);
                    }
                }
            }
            if (!dead.isEmpty()) {
                list.removeAll(dead);
            }
        }
    }

    public int getActiveConnectionCount() {
        Set<SseEmitter> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CopyOnWriteArrayList<SseEmitter> list : customerEmitters.values()) {
            unique.addAll(list);
        }
        return unique.size();
    }
}
