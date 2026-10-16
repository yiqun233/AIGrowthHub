package io.growthhub.studylog;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
import io.growthhub.common.BaseEntity;
@Entity @Table(name="study_log")
public class StudyLog extends BaseEntity {
    public LocalDate studyDate;
    public UUID learningStageId;
    @Column(columnDefinition="text") public String content;
    public int durationMinutes;
    public String activityType="LEARNING";
    @Column(columnDefinition="text") public String achievements="";
    @Column(columnDefinition="text") public String blockers="";
    @ElementCollection @CollectionTable(name="study_log_task",joinColumns=@JoinColumn(name="study_log_id"))
    @Column(name="task_id") public Set<UUID> taskIds=new LinkedHashSet<>();
}
