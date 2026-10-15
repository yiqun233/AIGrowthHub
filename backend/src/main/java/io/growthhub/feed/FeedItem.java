package io.growthhub.feed;
import io.growthhub.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="feed_item")
public class FeedItem extends BaseEntity {
 public String sourceId;public String externalId;public String url;public String title;
 @Column(columnDefinition="text") public String content;
 public Instant publishedAt;public boolean favorite;public String fingerprint;
}
