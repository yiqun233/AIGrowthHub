package io.growthhub.weekly;
import io.growthhub.common.ApiException;
import io.growthhub.task.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/tasks")
public class WeeklyAssignmentController {
 private final TaskRepository tasks;private final WeeklyPlanRepository plans;
 public WeeklyAssignmentController(TaskRepository tasks,WeeklyPlanRepository plans){this.tasks=tasks;this.plans=plans;}
 public record Input(@NotNull Long version,UUID weeklyPlanId,LocalDate scheduledDate){}
 @PatchMapping("/{id}/weekly-plan") @Transactional public TaskController.View assign(@PathVariable UUID id,@Valid @RequestBody Input i){
  var t=tasks.findById(id).orElseThrow(ApiException::missing);ApiException.version(t.version,i.version());
  LocalDate date=i.scheduledDate()==null?t.scheduledDate:i.scheduledDate();
  if(i.weeklyPlanId()!=null){var p=plans.findById(i.weeklyPlanId()).orElseThrow(()->new ApiException(400,"INVALID_WEEKLY_PLAN","周计划不存在"));
   if(p.status!=WeeklyPlan.Status.ACTIVE)throw new ApiException(409,"ARCHIVED_WEEKLY_PLAN","周计划已归档");
   if(date.isBefore(p.weekStart)||date.isAfter(p.weekStart.plusDays(6)))throw new ApiException(400,"WEEK_DATE_MISMATCH","任务日期不在所选周计划内，请同时选择该周日期");
  }
  t.weeklyPlanId=i.weeklyPlanId();t.scheduledDate=date;return TaskController.View.of(tasks.saveAndFlush(t));
 }
}
