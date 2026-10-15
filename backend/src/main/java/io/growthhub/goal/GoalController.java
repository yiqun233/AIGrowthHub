package io.growthhub.goal;
import io.growthhub.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/api/v1/goals")
public class GoalController {
    private final GoalRepository goals;
    public GoalController(GoalRepository goals) { this.goals=goals; }
    public record Input(@NotBlank @Size(max=200) String title, @Size(max=20000) String description,
        LocalDate startDate, LocalDate targetDate, @Min(1) @Max(3) Integer priority) {}
    public record Update(@NotNull Long version, @NotBlank @Size(max=200) String title,
        @Size(max=20000) String description, LocalDate startDate, LocalDate targetDate,
        @NotNull @Min(1) @Max(3) Integer priority, @NotNull Goal.Status status) {}
    public record View(UUID id,String title,String description,LocalDate startDate,LocalDate targetDate,int priority,Goal.Status status,Long version) {
        public static View of(Goal g) { return new View(g.id,g.title,g.description,g.startDate,g.targetDate,g.priority,g.status,g.version); }
    }
    @GetMapping public List<View> list() { return goals.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(View::of).toList(); }
    private void dates(LocalDate start, LocalDate end) {
        if (start!=null && end!=null && end.isBefore(start)) throw new ApiException(400,"DATE_RANGE","目标结束日期不能早于开始日期");
    }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
    public View create(@Valid @RequestBody Input i) {
        dates(i.startDate(),i.targetDate()); var g=new Goal();
        g.title=i.title().strip(); g.description=Objects.requireNonNullElse(i.description(),"");
        g.startDate=i.startDate();g.targetDate=i.targetDate();g.priority=i.priority()==null?2:i.priority();
        return View.of(goals.saveAndFlush(g));
    }
    @PatchMapping("/{id}") @Transactional public View update(@PathVariable UUID id,@Valid @RequestBody Update i) {
        var g=goals.findById(id).orElseThrow(ApiException::missing); ApiException.version(g.version,i.version());
        dates(i.startDate(),i.targetDate());g.title=i.title().strip();g.description=Objects.requireNonNullElse(i.description(),"");
        g.startDate=i.startDate();g.targetDate=i.targetDate();g.priority=i.priority();g.status=i.status();
        return View.of(goals.saveAndFlush(g));
    }
}
