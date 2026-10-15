package io.growthhub.goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface GoalRepository extends JpaRepository<Goal,UUID> { org.springframework.data.domain.Page<Goal> findByStatus(Goal.Status status,org.springframework.data.domain.Pageable pageable); }
