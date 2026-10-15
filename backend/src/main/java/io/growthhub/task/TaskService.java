package io.growthhub.task;
import io.growthhub.common.ApiException;
import io.growthhub.goal.GoalRepository;
import io.growthhub.goal.Goal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TaskService {
    private final TaskRepository tasks;private final GoalRepository goals;
    public TaskService(TaskRepository tasks,GoalRepository goals){this.tasks=tasks;this.goals=goals;}
    private void goal(UUID id, UUID previous) {
        if(id==null || id.equals(previous)) return;
        var g=goals.findById(id).orElseThrow(()->new ApiException(400,"INVALID_GOAL","关联目标不存在"));
        if(g.status!=Goal.Status.ACTIVE) throw new ApiException(400,"ARCHIVED_GOAL","不能关联已归档目标");
    }
    @Transactional public TaskController.View create(TaskController.Input i) {
        goal(i.goalId(),null);var t=new Task();
        t.title=i.title().strip();t.detail=Objects.requireNonNullElse(i.detail(),"");t.goalId=i.goalId();
        t.scheduledDate=i.scheduledDate();t.estimateMinutes=i.estimateMinutes();t.priority=i.priority()==null?2:i.priority();
        t.acceptanceCriteria=Objects.requireNonNullElse(i.acceptanceCriteria(),"");return TaskController.View.of(tasks.saveAndFlush(t));
    }
    @Transactional public TaskController.View update(UUID id,TaskController.Update i) {
        var t=tasks.findById(id).orElseThrow(ApiException::missing);ApiException.version(t.version,i.version());
        if(i.title()!=null) { if(i.title().isBlank()) throw new ApiException(400,"INVALID_TITLE","任务标题不能为空");t.title=i.title().strip(); }
        if(i.detail()!=null)t.detail=i.detail();
        if(Boolean.TRUE.equals(i.clearGoal()))t.goalId=null;
        else if(i.goalId()!=null){goal(i.goalId(),t.goalId);t.goalId=i.goalId();}
        if(i.scheduledDate()!=null)t.scheduledDate=i.scheduledDate();
        if(i.estimateMinutes()!=null)t.estimateMinutes=i.estimateMinutes();
        if(i.priority()!=null)t.priority=i.priority();
        if(i.acceptanceCriteria()!=null)t.acceptanceCriteria=i.acceptanceCriteria();
        if(i.status()!=null && i.status()!=t.status){t.status=i.status();t.completedAt=t.status==Task.Status.DONE?Instant.now():null;}
        return TaskController.View.of(tasks.saveAndFlush(t));
    }
}
