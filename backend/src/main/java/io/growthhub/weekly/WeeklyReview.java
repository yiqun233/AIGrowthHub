package io.growthhub.weekly;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
@Entity @Table(name="weekly_review")
public class WeeklyReview extends BaseEntity {
    public LocalDate weekStart;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public String sourceSnapshot;
    public String sourceHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public String aiDraft;
    @Column(columnDefinition="text") public String finalContent="";
    @Enumerated(EnumType.STRING) public Status status=Status.DRAFT;
    public Instant confirmedAt;
    public enum Status { DRAFT, CONFIRMED }
}
