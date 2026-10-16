package io.growthhub.stage;

import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="learning_stage_review")
public class LearningStageReview extends BaseEntity {
    public UUID stageId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public String sourceSnapshot;
    public String sourceHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public String aiDraft;
    @Column(columnDefinition="text") public String finalContent="";
    @Enumerated(EnumType.STRING) public Status status=Status.DRAFT;
    public Instant confirmedAt;
    public enum Status { DRAFT, CONFIRMED }
}
