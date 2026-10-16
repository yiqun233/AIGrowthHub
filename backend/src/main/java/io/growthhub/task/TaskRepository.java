package io.growthhub.task;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface TaskRepository extends JpaRepository<Task,UUID>,JpaSpecificationExecutor<Task> {
 long countByIdIn(java.util.Collection<UUID> ids);
 java.util.List<Task> findByLearningStageIdOrderByScheduledDateAscCreatedAtAsc(UUID learningStageId);
}
