package nl.invokedynamic.demo.waitinglist.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class HttpTableInventoryClient implements TableInventoryClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTableInventoryClient.class);
    private final RestClient restClient;

    public HttpTableInventoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableResponse(UUID id, String tableNumber, int capacity, String zone, String status) {}

    @Override
    public int getReleasedCapacity(UUID restaurantId, List<UUID> releasedTableIds) {
        if (releasedTableIds == null || releasedTableIds.isEmpty()) {
            return 0;
        }
        try {
            List<TableResponse> tables = restClient.get()
                    .uri("/api/v1/restaurants/{id}/tables", restaurantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (tables == null) return 0;
            Set<UUID> targetIds = new HashSet<>(releasedTableIds);
            return tables.stream()
                    .filter(t -> targetIds.contains(t.id()))
                    .mapToInt(TableResponse::capacity)
                    .sum();
        } catch (Exception e) {
            log.warn("Failed to fetch released tables capacity for restaurant {}: {}", restaurantId, e.getMessage());
            return 0;
        }
    }
}
