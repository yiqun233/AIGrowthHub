package io.growthhub.coach;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class AiCoachController {
    private final AiCoachService service;private final AiSettingsService settings;
    public AiCoachController(AiCoachService service,AiSettingsService settings){this.service=service;this.settings=settings;}
    @GetMapping("/settings") public AiContracts.Settings settings(){return settings.get();}
    @PatchMapping("/settings") public AiContracts.Settings settings(@Valid @RequestBody AiContracts.SettingsInput i){return settings.update(i);}
    @PostMapping("/ai/analyses") public ResponseEntity<AiCoachService.AnalysisView> create(@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody AiContracts.Generate i){return ResponseEntity.accepted().body(service.create(key,i));}
    @GetMapping("/ai/analyses") public AiCoachService.History history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.history(page,size);}
    @GetMapping("/ai/analyses/{id}") public AiCoachService.AnalysisView get(@PathVariable UUID id){return service.get(id);}
    @GetMapping("/plan-suggestions/{id}") public AiCoachService.SuggestionView suggestion(@PathVariable UUID id){return service.suggestion(id);}
    @PatchMapping("/plan-suggestions/{id}") public AiCoachService.SuggestionView edit(@PathVariable UUID id,@Valid @RequestBody AiContracts.Edit i){return service.edit(id,i);}
    @PostMapping("/plan-suggestions/{id}/confirm") public AiContracts.Confirmed confirm(@PathVariable UUID id,@Valid @RequestBody AiContracts.Version i){return service.confirm(id,i.version());}
    @PostMapping("/plan-suggestions/{id}/reject") public AiCoachService.SuggestionView reject(@PathVariable UUID id,@Valid @RequestBody AiContracts.Version i){return service.reject(id,i.version());}
}
