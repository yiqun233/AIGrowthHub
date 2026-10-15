package io.growthhub.achievement;
import io.growthhub.common.ApiException;
import io.growthhub.goal.*;
import io.growthhub.task.TaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/v1/achievements")
public class AchievementController {
 private final AchievementRepository repo;private final GoalRepository goals;private final TaskRepository tasks;
 public AchievementController(AchievementRepository repo,GoalRepository goals,TaskRepository tasks){this.repo=repo;this.goals=goals;this.tasks=tasks;}
 public record Input(@NotBlank @Size(max=200) String title,@NotNull @Size(max=20000) String description,
  @NotNull @Size(max=2000) String url,@NotNull Achievement.Kind kind,@NotNull LocalDate achievementDate,UUID goalId,UUID taskId){}
 public record Update(@NotNull Long version,@NotNull @Valid Input data,@NotNull Boolean archived){}
 public record View(UUID id,String title,String description,String url,Achievement.Kind kind,LocalDate achievementDate,UUID goalId,UUID taskId,boolean archived,Long version){
  static View of(Achievement a){return new View(a.id,a.title,a.description,a.url,a.kind,a.achievementDate,a.goalId,a.taskId,a.archived,a.version);}
 }
 public record PageView(List<View> items,long total,int page,int size){}
 private void fill(Achievement a,Input i){
  if(!i.url().isBlank())try{URI u=URI.create(i.url());if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new ApiException(400,"INVALID_URL","成果链接必须是有效的 HTTP/HTTPS 地址");}
  if(i.goalId()!=null&&!i.goalId().equals(a.goalId)){
   var g=goals.findById(i.goalId()).orElseThrow(()->new ApiException(400,"INVALID_GOAL","目标不存在"));
   if(g.status!=Goal.Status.ACTIVE)throw new ApiException(400,"ARCHIVED_GOAL","不能关联已归档目标");
  }
  if(i.taskId()!=null&&!tasks.existsById(i.taskId()))throw new ApiException(400,"INVALID_TASK","任务不存在");
  a.title=i.title().strip();a.description=i.description();a.url=i.url().strip();a.kind=i.kind();a.achievementDate=i.achievementDate();a.goalId=i.goalId();a.taskId=i.taskId();
 }
 @GetMapping @Transactional(readOnly=true) public PageView list(@RequestParam(defaultValue="false") boolean archived,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  if(page<0||size<1||size>100||from!=null&&to!=null&&to.isBefore(from))throw new ApiException(400,"INVALID_QUERY","分页或日期范围不正确");
  Specification<Achievement> spec=(r,q,b)->b.equal(r.get("archived"),archived);
  if(from!=null)spec=spec.and((r,q,b)->b.greaterThanOrEqualTo(r.get("achievementDate"),from));if(to!=null)spec=spec.and((r,q,b)->b.lessThanOrEqualTo(r.get("achievementDate"),to));
  var p=repo.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"achievementDate","id")));return new PageView(p.stream().map(View::of).toList(),p.getTotalElements(),page,size);
 }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional public View create(@Valid @RequestBody Input i){var a=new Achievement();fill(a,i);return View.of(repo.saveAndFlush(a));}
 @PatchMapping("/{id}") @Transactional public View update(@PathVariable UUID id,@Valid @RequestBody Update i){var a=repo.findById(id).orElseThrow(ApiException::missing);ApiException.version(a.version,i.version());fill(a,i.data());a.archived=i.archived();return View.of(repo.saveAndFlush(a));}
}
