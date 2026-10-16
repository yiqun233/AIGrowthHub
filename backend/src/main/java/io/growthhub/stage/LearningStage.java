package io.growthhub.stage;

import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name="learning_stage")
public class LearningStage extends BaseEntity {
    public String title;
    @Column(columnDefinition="text") public String desiredOutcome="";
    @Column(columnDefinition="text") public String acceptanceCriteria="";
    public LocalDate startDate;
    public LocalDate targetDate;
    @Enumerated(EnumType.STRING) public Status status=Status.ACTIVE;
    @Enumerated(EnumType.STRING) public Outcome outcome;
    public enum Status { ACTIVE, PAUSED, CLOSED }
    public enum Outcome { ACHIEVED, PARTIAL, RESCOPED, ABANDONED }
}
