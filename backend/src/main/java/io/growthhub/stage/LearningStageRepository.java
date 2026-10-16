package io.growthhub.stage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LearningStageRepository extends JpaRepository<LearningStage,UUID> {
    List<LearningStage> findAllByOrderByCreatedAtDesc();
    boolean existsByStatus(LearningStage.Status status);
}
