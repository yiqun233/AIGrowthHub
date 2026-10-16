package io.growthhub.weekly;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview,UUID> {
    java.util.List<WeeklyReview> findByWeekStartIn(java.util.Collection<java.time.LocalDate> weekStarts);
    Optional<WeeklyReview> findByWeekStart(LocalDate weekStart);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from WeeklyReview r where r.id=:id")
    Optional<WeeklyReview> lock(@Param("id") UUID id);
}
