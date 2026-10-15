package io.growthhub.coach;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PlanSuggestionRepository extends JpaRepository<PlanSuggestion,UUID> {
    Optional<PlanSuggestion> findByAnalysisId(UUID analysisId);
    List<PlanSuggestion> findByStatus(PlanSuggestion.Status status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from PlanSuggestion s where s.id=:id") Optional<PlanSuggestion> lock(@Param("id") UUID id);
    java.util.List<PlanSuggestion> findByAnalysisIdIn(java.util.Collection<java.util.UUID> ids);
}
