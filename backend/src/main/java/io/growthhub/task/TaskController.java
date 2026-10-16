package io.growthhub.task;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskRepository tasks;private final TaskService service;
    public TaskController(TaskRepository tasks,TaskService service){this.tasks=tasks;this.service=service;}
    public record Input(@NotBlank @Size(max=200) String title,@Size(max=20000) String detail,UUID goalId,
        @NotNull LocalDate scheduledDate,@NotNull @Min(1) @Max(1440) Integer estimateMinutes,
        @Min(1) @Max(3) Integer priority,@Size(max=5000) String acceptanceCriteria,UUID learningStageId){}
    public record Update(@NotNull Long version,@Size(max=200) String title,@Size(max=20000) String detail,
        UUID goalId,Boolean clearGoal,LocalDate scheduledDate,@Min(1) @Max(1440) Integer estimateMinutes,
        @Min(1) @Max(3) Integer priority,Task.Status status,@Size(max=5000) String acceptanceCriteria,UUID learningStageId,Boolean clearLearningStage){}
    public record View(UUID id,UUID goalId,String title,String detail,LocalDate scheduledDate,int estimateMinutes,
        int priority,Task.Status status,String acceptanceCriteria,Instant completedAt,Long version,UUID weeklyPlanId,UUID learningStageId){
        public static View of(Task t){return new View(t.id,t.goalId,t.title,t.detail,t.scheduledDate,t.estimateMinutes,t.priority,t.status,t.acceptanceCriteria,t.completedAt,t.version,t.weeklyPlanId,t.learningStageId);}
    }
    public record Result(List<View> items,long total,int page,int size){}
    @GetMapping public Result list(@RequestParam(required=false) LocalDate date,@RequestParam(required=false) Task.Status status,@RequestParam(required=false) UUID learningStageId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        if(page<0 || size<1 || size>100)throw new io.growthhub.common.ApiException(400,"INVALID_PAGE","每页最多 100 条");
        Specification<Task> spec=(r,q,b)->b.conjunction();
        if(date!=null)spec=spec.and((r,q,b)->b.equal(r.get("scheduledDate"),date));
        if(status!=null)spec=spec.and((r,q,b)->b.equal(r.get("status"),status));
        if(learningStageId!=null)spec=spec.and((r,q,b)->b.equal(r.get("learningStageId"),learningStageId));
        var result=tasks.findAll(spec,PageRequest.of(page,size,Sort.by("scheduledDate","priority").and(Sort.by(Sort.Direction.DESC,"createdAt"))));
        return new Result(result.map(View::of).getContent(),result.getTotalElements(),page,size);
    }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public View create(@Valid @RequestBody Input i){return service.create(i);}
    @PatchMapping("/{id}") public View update(@PathVariable UUID id,@Valid @RequestBody Update i){return service.update(id,i);}
}
