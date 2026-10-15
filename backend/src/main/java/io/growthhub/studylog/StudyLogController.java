package io.growthhub.studylog;
import io.growthhub.common.ApiException;
import io.growthhub.task.TaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/study-logs")
public class StudyLogController {
    private final StudyLogRepository logs;private final TaskRepository tasks;
    public StudyLogController(StudyLogRepository logs,TaskRepository tasks){this.logs=logs;this.tasks=tasks;}
    public record Input(@NotNull LocalDate studyDate,@NotBlank @Size(max=50000) String content,
        @NotNull @Min(0) @Max(1440) Integer durationMinutes,@Size(max=20000) String achievements,
        @Size(max=20000) String blockers,@Size(max=100) Set<UUID> taskIds){}
    public record Update(@NotNull Long version,@NotNull @Valid Input data){}
    public record View(UUID id,LocalDate studyDate,String content,int durationMinutes,String achievements,String blockers,Set<UUID> taskIds,Long version){
        public static View of(StudyLog l){return new View(l.id,l.studyDate,l.content,l.durationMinutes,l.achievements,l.blockers,Set.copyOf(l.taskIds),l.version);}
    }
    public record Result(List<View> items,long total,int page,int size){}
    private void fill(StudyLog l,Input i){
        var ids=i.taskIds()==null?Set.<UUID>of():i.taskIds();
        if(ids.stream().anyMatch(Objects::isNull) || tasks.countByIdIn(ids)!=ids.size())throw new ApiException(400,"INVALID_TASK","关联任务不存在");
        l.studyDate=i.studyDate();l.content=i.content();l.durationMinutes=i.durationMinutes();
        l.achievements=Objects.requireNonNullElse(i.achievements(),"");l.blockers=Objects.requireNonNullElse(i.blockers(),"");
        l.taskIds.clear();l.taskIds.addAll(ids);
    }
    @GetMapping @Transactional(readOnly=true) public Result list(@RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size){
        if(page<0 || size<1 || size>100)throw new ApiException(400,"INVALID_PAGE","每页最多 100 条");
        if(from!=null && to!=null && to.isBefore(from))throw new ApiException(400,"DATE_RANGE","查询结束日期不能早于开始日期");
        Specification<StudyLog> spec=(r,q,b)->b.conjunction();
        if(from!=null)spec=spec.and((r,q,b)->b.greaterThanOrEqualTo(r.get("studyDate"),from));
        if(to!=null)spec=spec.and((r,q,b)->b.lessThanOrEqualTo(r.get("studyDate"),to));
        var result=logs.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"studyDate","createdAt")));
        return new Result(result.stream().map(View::of).toList(),result.getTotalElements(),page,size);
    }
    @GetMapping("/{id}") @Transactional(readOnly=true) public View get(@PathVariable UUID id){return View.of(logs.findById(id).orElseThrow(ApiException::missing));}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional public View create(@Valid @RequestBody Input i){
        var l=new StudyLog();fill(l,i);return View.of(logs.saveAndFlush(l));
    }
    @PatchMapping("/{id}") @Transactional public View update(@PathVariable UUID id,@Valid @RequestBody Update i){
        var l=logs.findById(id).orElseThrow(ApiException::missing);ApiException.version(l.version,i.version());fill(l,i.data());
        return View.of(logs.saveAndFlush(l));
    }
}
