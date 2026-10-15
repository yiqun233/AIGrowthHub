package io.growthhub.work;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface AiWorkRepository extends JpaRepository<AiWork,UUID>{
 Optional<AiWork> findByRequestKey(String key);
 Optional<AiWork> findFirstByStatusOrderByCreatedAtAsc(String status);
 List<AiWork> findByStatus(String status);
 List<AiWork> findTop20ByKindAndTargetIdOrderByCreatedAtDesc(String kind,UUID targetId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from AiWork w where w.id=:id") Optional<AiWork> lock(UUID id);
}
