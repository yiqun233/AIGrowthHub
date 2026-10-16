package io.growthhub.stage;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface LearningStageReviewRepository extends JpaRepository<LearningStageReview,UUID> {
    Optional<LearningStageReview> findByStageId(UUID stageId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from LearningStageReview r where r.id=:id") Optional<LearningStageReview> lock(UUID id);
}
