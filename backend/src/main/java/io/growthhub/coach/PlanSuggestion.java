package io.growthhub.coach;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="plan_suggestion")
public class PlanSuggestion extends BaseEntity {
    public UUID analysisId;
    public LocalDate targetDate;
    @JdbcTypeCode(SqlTypes.JSON) public String proposedTasksJson;
    @Enumerated(EnumType.STRING) public Status status=Status.DRAFT;
    public Instant confirmedAt;
    public enum Status { DRAFT,CONFIRMED,REJECTED,SUPERSEDED }
}
