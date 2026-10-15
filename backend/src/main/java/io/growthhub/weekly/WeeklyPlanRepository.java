package io.growthhub.weekly;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan,UUID> {
    Optional<WeeklyPlan> findByWeekStart(LocalDate weekStart);
}
