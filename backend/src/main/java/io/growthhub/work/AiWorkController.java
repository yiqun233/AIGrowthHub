package io.growthhub.work;
import io.growthhub.weekly.WeeklyContracts;
import io.growthhub.stage.StageContracts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class AiWorkController {
 private final AiWorkService service;public AiWorkController(AiWorkService service){this.service=service;}
 public record Apply(@NotNull Long version,@NotNull UUID workId){}
 @PostMapping("/reviews/weekly/{id}/generate") @ResponseStatus(HttpStatus.ACCEPTED) public AiWorkService.View weekly(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody AiWorkService.Input input){return service.create("WEEKLY_REVIEW",id,key,input);}
 @PostMapping("/stage-reviews/{id}/generate") @ResponseStatus(HttpStatus.ACCEPTED) public AiWorkService.View stage(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody AiWorkService.Input input){return service.create("STAGE_REVIEW",id,key,input);}
 @PostMapping("/feed/items/{id}/summarize") @ResponseStatus(HttpStatus.ACCEPTED) public AiWorkService.View feed(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody AiWorkService.Input input){return service.create("FEED_SUMMARY",id,key,input);}
 @GetMapping("/ai/works/{id}") public AiWorkService.View get(@PathVariable UUID id){return service.get(id);}
 @GetMapping("/ai/works") public List<AiWorkService.View> history(@RequestParam String kind,@RequestParam UUID targetId){return service.history(kind,targetId);}
 @PostMapping("/reviews/weekly/{id}/apply-ai") public WeeklyContracts.Review apply(@PathVariable UUID id,@Valid @RequestBody Apply i){return service.apply(id,i.workId(),i.version());}
 @PostMapping("/stage-reviews/{id}/apply-ai") public StageContracts.Review applyStage(@PathVariable UUID id,@Valid @RequestBody Apply i){return service.applyStage(id,i.workId(),i.version());}
}
