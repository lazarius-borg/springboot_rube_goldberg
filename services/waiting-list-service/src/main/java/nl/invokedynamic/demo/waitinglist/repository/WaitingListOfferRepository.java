package nl.invokedynamic.demo.waitinglist.repository;

import nl.invokedynamic.demo.waitinglist.domain.WaitingListOfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WaitingListOfferRepository extends JpaRepository<WaitingListOfferEntity, UUID> {
    List<WaitingListOfferEntity> findByStatusAndExpiresAtBefore(String status, Instant now);
}
