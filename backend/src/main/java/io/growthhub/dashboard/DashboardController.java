package io.growthhub.dashboard;
import io.growthhub.task.*;
import io.growthhub.studylog.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final TaskRepository tasks;private final StudyLogRepository logs;private final ZoneId zone;
    public DashboardController(TaskRepository tasks,StudyLogRepository logs,@Value("${app.timezone}") String zone){this.tasks=tasks;this.logs=logs;this.zone=ZoneId.of(zone);}
    public record View(LocalDate date,String timezone,List<TaskController.View> tasks,List<StudyLogController.View> recentLogs,
        long plannedCount,long completedCount,long weekDurationMinutes,long overdueCount){}
    @GetMapping @Transactional(readOnly=true) public View get(@RequestParam(required=false) LocalDate date){
        LocalDate day=date==null?LocalDate.now(zone):date;LocalDate start=day.with(DayOfWeek.MONDAY);
        var today=tasks.findAll((r,q,b)->b.equal(r.get("scheduledDate"),day),Sort.by("priority","createdAt"));
        var recent=logs.findAll(PageRequest.of(0,5,Sort.by(Sort.Direction.DESC,"studyDate","createdAt"))).stream().map(StudyLogController.View::of).toList();
        var week=logs.findAll((r,q,b)->b.between(r.get("studyDate"),start,start.plusDays(6)));
        long active=today.stream().filter(t->t.status!=Task.Status.CANCELLED).count();
        long done=today.stream().filter(t->t.status==Task.Status.DONE).count();
        long overdue=tasks.count((r,q,b)->b.and(b.lessThan(r.get("scheduledDate"),day),r.get("status").in(Task.Status.TODO,Task.Status.IN_PROGRESS)));
        return new View(day,zone.toString(),today.stream().map(TaskController.View::of).toList(),recent,active,done,
            week.stream().mapToLong(l->l.durationMinutes).sum(),overdue);
    }
}
