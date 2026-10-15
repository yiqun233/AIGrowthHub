package io.growthhub.coach;
import io.growthhub.common.ApiException;
import io.growthhub.goal.*;
import io.growthhub.studylog.*;
import io.growthhub.task.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AiContextService {
    public record Snapshot(StudyLogController.View source,List<StudyLogController.View> recentLogs,
        List<GoalController.View> goals,List<TaskController.View> openTasks,List<TaskController.View> targetTasks,
        LocalDate targetDate,int budgetMinutes,String feedback,String preferences,int omittedLogs) {}
    private final StudyLogRepository logs;private final GoalRepository goals;private final TaskRepository tasks;private final AiJson json;private final AiSettingRepository settings;
    public AiContextService(StudyLogRepository logs,GoalRepository goals,TaskRepository tasks,AiJson json,AiSettingRepository settings){this.logs=logs;this.goals=goals;this.tasks=tasks;this.json=json;this.settings=settings;}
    @Transactional(readOnly=true) public Snapshot build(AiContracts.Generate request,String preferences){
        var source=logs.findById(request.sourceLogId()).orElseThrow(ApiException::missing);
        if(source.content.length()+source.achievements.length()+source.blockers.length()>24000)
            throw new ApiException(400,"CONTEXT_TOO_LARGE","当前记录过长，请缩短到 24000 字符以内再分析，原文仍完整保留");
        var goalPage=goals.findByStatus(Goal.Status.ACTIVE,PageRequest.of(0,30,Sort.by("priority","id")));
        if(goalPage.getTotalElements()>30)throw new ApiException(400,"CONTEXT_TOO_LARGE","目标较多，请先归档不再使用的目标");
        var active=goalPage.stream().filter(g->g.status==Goal.Status.ACTIVE).map(GoalController.View::of).toList();
        var backlog=tasks.findAll((root,q,b)->root.get("status").in(Task.Status.TODO,Task.Status.IN_PROGRESS),PageRequest.of(0,50,Sort.by("scheduledDate","id"))).stream().map(TaskController.View::of).toList();
        var openMap=new LinkedHashMap<UUID,TaskController.View>();
        tasks.findAllById(source.taskIds).stream().filter(t->t.status==Task.Status.TODO||t.status==Task.Status.IN_PROGRESS).forEach(t->openMap.put(t.id,TaskController.View.of(t)));
        backlog.forEach(t->openMap.putIfAbsent(t.id(),t));
        var open=openMap.values().stream().limit(50).toList();
        var target=tasks.findAll((root,q,b)->b.and(b.equal(root.get("scheduledDate"),request.targetDate()),b.notEqual(root.get("status"),Task.Status.CANCELLED)),Sort.by("id")).stream().map(TaskController.View::of).toList();
        if(target.size()>50)throw new ApiException(400,"CONTEXT_TOO_LARGE","目标日期任务过多，请先整理任务");
        var recent=logs.findAll((root,q,b)->b.and(b.between(root.get("studyDate"),source.studyDate.minusDays(13),source.studyDate),b.notEqual(root.get("id"),source.id)),PageRequest.of(0,14,Sort.by(Sort.Direction.DESC,"studyDate","createdAt")));
        var selected=new ArrayList<StudyLogController.View>();int omitted=0;
        for(var log:recent){
            if(log.content.length()+log.achievements.length()+log.blockers.length()>6000){omitted++;continue;}
            selected.add(StudyLogController.View.of(log));
        }
        var snapshot=new Snapshot(StudyLogController.View.of(source),selected,active,open,target,request.targetDate(),request.budgetMinutes(),Objects.requireNonNullElse(request.feedback(),""),preferences,omitted);
        while(json.write(snapshot).length()>60000 && !selected.isEmpty()){
            selected.removeLast();omitted++;snapshot=new Snapshot(snapshot.source(),List.copyOf(selected),active,open,target,request.targetDate(),request.budgetMinutes(),snapshot.feedback(),preferences,omitted);
        }
        if(json.write(snapshot).length()>60000)throw new ApiException(400,"CONTEXT_TOO_LARGE","目标或任务内容过长，请整理后再试");
        return snapshot;
    }
    public void fresh(Snapshot s){
        checkLog(s.source());s.recentLogs().forEach(this::checkLog);
        if(!settings.findById(1).orElseThrow().preferences.equals(s.preferences()))stale();
        for(var g:s.goals())if(!goals.findById(g.id()).map(now->now.version.equals(g.version()) && now.status==g.status()).orElse(false))stale();
        var referenced=new LinkedHashMap<UUID,TaskController.View>();s.openTasks().forEach(t->referenced.put(t.id(),t));s.targetTasks().forEach(t->referenced.put(t.id(),t));
        for(var t:referenced.values())if(!tasks.findById(t.id()).map(now->now.version.equals(t.version())).orElse(false))stale();
    }
    private void checkLog(StudyLogController.View log){if(!logs.findById(log.id()).map(now->now.version.equals(log.version())).orElse(false))stale();}
    private void stale(){throw new ApiException(409,"STALE_CONTEXT","来源记录、目标或任务已变化，请重新生成建议");}
}
