package io.growthhub.task;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import io.growthhub.common.BaseEntity;
@Entity @Table(name="learning_task")
public class Task extends BaseEntity {
    public UUID goalId;
    public UUID weeklyPlanId;
    public UUID learningStageId;
    public String title;
    @Column(columnDefinition="text") public String detail="";
    public LocalDate scheduledDate;
    public int estimateMinutes=30;
    public int priority=2;
    @Enumerated(EnumType.STRING) public Status status=Status.TODO;
    public String source="MANUAL";
    @Column(columnDefinition="text") public String acceptanceCriteria="";
    public Instant completedAt;
    public enum Status { TODO, IN_PROGRESS, DONE, CANCELLED }
}
