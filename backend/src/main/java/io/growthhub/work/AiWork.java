package io.growthhub.work;
import io.growthhub.common.BaseEntity;
import io.growthhub.coach.AiContracts;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="ai_work")
public class AiWork extends BaseEntity {
 public String kind;public UUID targetId;public String promptVersion="work-v1";public String schemaVersion="work-v1";
 @Enumerated(EnumType.STRING) public AiContracts.Provider provider;
 public String model;public String requestKey;public String requestHash;public String sourceHash;
 @JdbcTypeCode(SqlTypes.JSON) public String inputSnapshot;
 @JdbcTypeCode(SqlTypes.JSON) public String outputJson;
 public String status="PENDING";public int attemptCount;public Long inputTokens;public Long outputTokens;public Long durationMs;
 public String errorCode;public String errorMessage;public Instant startedAt;public Instant finishedAt;
}
