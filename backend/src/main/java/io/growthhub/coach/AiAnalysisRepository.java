package io.growthhub.coach;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis,UUID> {
    Optional<AiAnalysis> findByRequestKey(String key);
    boolean existsByStatusIn(Collection<AiAnalysis.Status> statuses);
    long countByCreatedAtGreaterThanEqual(Instant since);
    List<AiAnalysis> findByStatus(AiAnalysis.Status status);
    @Query(value="select * from ai_analysis where status='PENDING' order by created_at limit 1 for update skip locked",nativeQuery=true)
    Optional<AiAnalysis> claimNext();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AiAnalysis a where a.id=:id") Optional<AiAnalysis> lock(@Param("id") UUID id);
}
