package io.growthhub.feed;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="feed_candidate")
public class FeedCandidate extends BaseEntity {
 public UUID itemId;public String title;
 @Column(columnDefinition="text") public String detail;
 @Column(columnDefinition="text") public String acceptanceCriteria;
 public int estimateMinutes;public LocalDate scheduledDate;public String status="DRAFT";public UUID taskId;
}
