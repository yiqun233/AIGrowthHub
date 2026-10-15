package io.growthhub.weekly;
import jakarta.validation.constraints.*;
import io.growthhub.studylog.StudyLogController;
import io.growthhub.task.TaskController;
import java.time.*;
import java.util.*;
public final class WeeklyContracts {
    private WeeklyContracts(){}
    public record PlanInput(@NotNull LocalDate weekStart,@NotBlank @Size(max=200) String theme,
        @NotNull @Min(0) @Max(10080) Integer budgetMinutes,@NotNull @Size(max=10000) String notes){}
    public record PlanUpdate(@NotNull Long version,@NotBlank @Size(max=200) String theme,
        @NotNull @Min(0) @Max(10080) Integer budgetMinutes,@NotNull @Size(max=10000) String notes,@NotNull WeeklyPlan.Status status){}
    public record Plan(UUID id,LocalDate weekStart,String theme,int budgetMinutes,String notes,WeeklyPlan.Status status,Long version){
        public static Plan of(WeeklyPlan p){return new Plan(p.id,p.weekStart,p.theme,p.budgetMinutes,p.notes,p.status,p.version);}
    }
    public record Summary(int logCount,long actualMinutes,int scheduledTaskCount,long scheduledMinutes,
        int scheduledDoneCount,int completedDuringWeekCount,long learningMinutes,long developmentMinutes){}
    public record Facts(LocalDate weekStart,LocalDate weekEnd,String timezone,Plan plan,Summary summary,
        List<StudyLogController.View> logs,List<TaskController.View> scheduledTasks,
        List<TaskController.View> completedDuringWeek,List<TaskController.View> linkedTasks){}
    public record Review(UUID id,LocalDate weekStart,String finalContent,WeeklyReview.Status status,Instant confirmedAt,
        Long version,boolean stale,Facts snapshot,Object aiDraft){}
    public record Overview(Facts facts,Review review){}
    public record ReviewInput(@NotNull LocalDate weekStart){}
    public record ReviewUpdate(@NotNull Long version,@NotNull @Size(max=30000) String finalContent){}
    public record Version(@NotNull Long version){}
}
