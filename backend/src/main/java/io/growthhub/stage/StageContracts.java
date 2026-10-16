package io.growthhub.stage;

import io.growthhub.achievement.AchievementController;
import io.growthhub.studylog.StudyLogController;
import io.growthhub.task.TaskController;
import io.growthhub.weekly.WeeklyContracts;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class StageContracts {
    private StageContracts(){}
    public record Input(@NotBlank @Size(max=200) String title,@NotNull @Size(max=10000) String desiredOutcome,
        @NotNull @Size(max=10000) String acceptanceCriteria,@NotNull LocalDate startDate,LocalDate targetDate){}
    public record Update(@NotNull Long version,@NotBlank @Size(max=200) String title,@NotNull @Size(max=10000) String desiredOutcome,
        @NotNull @Size(max=10000) String acceptanceCriteria,@NotNull LocalDate startDate,LocalDate targetDate){}
    public record Version(@NotNull Long version){}
    public record OutcomeInput(@NotNull Long version,@NotNull LearningStage.Outcome outcome){}
    public record Stage(UUID id,String title,String desiredOutcome,String acceptanceCriteria,LocalDate startDate,LocalDate targetDate,
        LearningStage.Status status,LearningStage.Outcome outcome,Long version,Instant createdAt,Instant updatedAt){
        public static Stage of(LearningStage s){return new Stage(s.id,s.title,s.desiredOutcome,s.acceptanceCriteria,s.startDate,s.targetDate,s.status,s.outcome,s.version,s.createdAt,s.updatedAt);}
    }
    public record WeeklyReview(UUID id,LocalDate weekStart,String status,String finalContent){}
    public record Summary(int logCount,long actualMinutes,int taskCount,int completedTaskCount,int achievementCount,int weeklyReviewCount){}
    public record Facts(Stage stage,String timezone,Summary summary,List<StudyLogController.View> logs,List<TaskController.View> tasks,
        List<AchievementController.View> achievements,List<WeeklyContracts.Plan> weeklyPlans,List<WeeklyReview> weeklyReviews){}
    public record Review(UUID id,UUID stageId,String finalContent,LearningStageReview.Status status,LearningStage.Outcome outcome,
        Instant confirmedAt,Long version,boolean stale,Facts snapshot,Object aiDraft){}
    public record Detail(Stage stage,Facts facts,Review review){}
    public record ReviewUpdate(@NotNull Long version,@NotNull @Size(max=30000) String finalContent){}
}
