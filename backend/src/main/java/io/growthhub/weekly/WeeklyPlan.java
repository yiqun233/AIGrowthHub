package io.growthhub.weekly;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="weekly_plan")
public class WeeklyPlan extends BaseEntity {
    public LocalDate weekStart;
    public java.util.UUID learningStageId;
    public String theme;
    public int budgetMinutes;
    @Column(columnDefinition="text") public String notes="";
    @Enumerated(EnumType.STRING) public Status status=Status.ACTIVE;
    public enum Status { ACTIVE, ARCHIVED }
}
