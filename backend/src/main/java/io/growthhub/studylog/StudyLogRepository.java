package io.growthhub.studylog;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface StudyLogRepository extends JpaRepository<StudyLog,UUID>,JpaSpecificationExecutor<StudyLog> {
 @Query("select coalesce(sum(l.durationMinutes),0) from StudyLog l where l.studyDate between :start and :end")
 long minutesBetween(java.time.LocalDate start,java.time.LocalDate end);
}
