package io.growthhub.achievement;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="achievement")
public class Achievement extends BaseEntity {
 public String title;
 @Column(columnDefinition="text") public String description="";
 public String url="";
 @Enumerated(EnumType.STRING) public Kind kind=Kind.PROJECT;
 public LocalDate achievementDate;public UUID goalId;public UUID taskId;public UUID learningStageId;public boolean archived;
 public enum Kind { PROJECT,CODE,ARTICLE,CERTIFICATE,OTHER }
}
