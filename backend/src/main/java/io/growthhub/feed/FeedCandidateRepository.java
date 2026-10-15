package io.growthhub.feed;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface FeedCandidateRepository extends JpaRepository<FeedCandidate,UUID>{
 Optional<FeedCandidate> findByItemId(UUID itemId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from FeedCandidate c where c.id=:id")
 Optional<FeedCandidate> lock(UUID id);
}
