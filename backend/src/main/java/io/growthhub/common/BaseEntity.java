package io.growthhub.common;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@MappedSuperclass
public abstract class BaseEntity {
    @Id public UUID id = UUID.randomUUID();
    @Version public Long version;
    @Column(nullable=false, updatable=false) public Instant createdAt;
    @Column(nullable=false) public Instant updatedAt;
    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
