package io.growthhub.achievement;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface AchievementRepository extends JpaRepository<Achievement,UUID>,JpaSpecificationExecutor<Achievement>{
 java.util.List<Achievement> findByLearningStageIdOrderByAchievementDateAsc(UUID learningStageId);
}
