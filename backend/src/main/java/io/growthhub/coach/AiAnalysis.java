package io.growthhub.coach;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="ai_analysis")
public class AiAnalysis extends BaseEntity {
    public UUID sourceLogId;
    public LocalDate targetDate;
    public int budgetMinutes;
    public String feedback="";
    @Enumerated(EnumType.STRING) public AiContracts.Provider provider;
    public String model;
    public String requestKey;
    public String requestHash;
    @JdbcTypeCode(SqlTypes.JSON) public String inputSnapshot;
    public String contextHash;
    @JdbcTypeCode(SqlTypes.JSON) public String outputJson;
    @Enumerated(EnumType.STRING) public Status status=Status.PENDING;
    public String promptVersion="daily-v1";
    public String schemaVersion="coach-v1";
    public int attemptCount;
    public Long durationMs;
    public Long inputTokens;
    public Long outputTokens;
    public String errorCode;
    public String errorMessage;
    public Instant startedAt;
    public Instant finishedAt;
    public enum Status { PENDING,RUNNING,SUCCEEDED,FAILED }
}
