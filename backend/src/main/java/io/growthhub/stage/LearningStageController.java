package io.growthhub.stage;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static io.growthhub.stage.StageContracts.*;

@RestController @RequestMapping("/api/v1")
public class LearningStageController {
 private final LearningStageService service;
 public LearningStageController(LearningStageService service){this.service=service;}
 @GetMapping("/stages") public List<Stage> list(){return service.list();}
 @GetMapping("/stages/{id}") public Detail get(@PathVariable UUID id){return service.detail(id);}
 @PostMapping("/stages") @ResponseStatus(HttpStatus.CREATED) public Stage create(@Valid @RequestBody Input i){return service.create(i);}
 @PatchMapping("/stages/{id}") public Stage update(@PathVariable UUID id,@Valid @RequestBody Update i){return service.update(id,i);}
 @PostMapping("/stages/{id}/pause") public Stage pause(@PathVariable UUID id,@Valid @RequestBody Version i){return service.status(id,i,LearningStage.Status.PAUSED);}
 @PostMapping("/stages/{id}/resume") public Stage resume(@PathVariable UUID id,@Valid @RequestBody Version i){return service.status(id,i,LearningStage.Status.ACTIVE);}
 @PostMapping("/stages/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) public Review createReview(@PathVariable UUID id){return service.createReview(id);}
 @GetMapping("/stage-reviews/{id}") public Review getReview(@PathVariable UUID id){return service.getReview(id);}
 @PatchMapping("/stage-reviews/{id}") public Review edit(@PathVariable UUID id,@Valid @RequestBody ReviewUpdate i){return service.editReview(id,i);}
 @PostMapping("/stage-reviews/{id}/refresh") public Review refresh(@PathVariable UUID id,@Valid @RequestBody Version i){return service.refresh(id,i);}
 @PostMapping("/stage-reviews/{id}/confirm") public Review confirm(@PathVariable UUID id,@Valid @RequestBody OutcomeInput i){return service.confirm(id,i);}
}
