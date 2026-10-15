package io.growthhub.weekly;
import jakarta.validation.Valid;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static io.growthhub.weekly.WeeklyContracts.*;
@RestController @RequestMapping("/api/v1")
public class WeeklyController {
    private final WeeklyService service;private final io.growthhub.common.BusinessClock clock;
    public WeeklyController(WeeklyService service,io.growthhub.common.BusinessClock clock){this.service=service;this.clock=clock;}
    @GetMapping("/weeks") public Overview overview(@RequestParam(required=false) LocalDate date){return service.overview(date==null?clock.today():date);}
    @GetMapping("/weekly-plans") public java.util.List<Plan> plans(){return service.plans();}
    @GetMapping("/reviews/weekly/{id}") public Review getReview(@PathVariable UUID id){return service.getReview(id);}
    @PostMapping("/weekly-plans") @ResponseStatus(HttpStatus.CREATED) public Plan createPlan(@Valid @RequestBody PlanInput i){return service.createPlan(i);}
    @PatchMapping("/weekly-plans/{id}") public Plan updatePlan(@PathVariable UUID id,@Valid @RequestBody PlanUpdate i){return service.updatePlan(id,i);}
    @PostMapping("/reviews/weekly") @ResponseStatus(HttpStatus.CREATED) public Review createReview(@Valid @RequestBody ReviewInput i){return service.createReview(i);}
    @PatchMapping("/reviews/weekly/{id}") public Review editReview(@PathVariable UUID id,@Valid @RequestBody ReviewUpdate i){return service.editReview(id,i);}
    @PostMapping("/reviews/weekly/{id}/refresh") public Review refresh(@PathVariable UUID id,@Valid @RequestBody Version i){return service.refresh(id,i);}
    @PostMapping("/reviews/weekly/{id}/confirm") public Review confirm(@PathVariable UUID id,@Valid @RequestBody Version i){return service.confirm(id,i);}
}
