package io.growthhub.goal;
import jakarta.persistence.*;
import java.time.LocalDate;
import io.growthhub.common.BaseEntity;
@Entity @Table(name="learning_goal")
public class Goal extends BaseEntity {
    public String title;
    @Column(columnDefinition="text") public String description = "";
    public LocalDate startDate;
    public LocalDate targetDate;
    public int priority = 2;
    @Enumerated(EnumType.STRING) public Status status = Status.ACTIVE;
    public enum Status { ACTIVE, ARCHIVED }
}
