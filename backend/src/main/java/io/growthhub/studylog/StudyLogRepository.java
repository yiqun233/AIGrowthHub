package io.growthhub.studylog;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface StudyLogRepository extends JpaRepository<StudyLog,UUID>,JpaSpecificationExecutor<StudyLog> {}
