package io.growthhub.coach;
import io.growthhub.common.ApiException;
import io.growthhub.task.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
@Service
public class AiCoachService {
    private final AiSettingRepository settings;private final AiAnalysisRepository analyses;private final PlanSuggestionRepository suggestions;
    private final TaskRepository tasks;private final AiConfiguration config;private final AiContextService context;
    private final AiResultValidator validator;private final AiJson json;private final JdbcTemplate jdbc;
    private static final TypeReference<List<AiContracts.ProposedTask>> TASKS=new TypeReference<>(){};
    public AiCoachService(AiSettingRepository settings,AiAnalysisRepository analyses,PlanSuggestionRepository suggestions,
        TaskRepository tasks,AiConfiguration config,AiContextService context,AiResultValidator validator,AiJson json,JdbcTemplate jdbc){
        this.settings=settings;this.analyses=analyses;this.suggestions=suggestions;this.tasks=tasks;this.config=config;
        this.context=context;this.validator=validator;this.json=json;this.jdbc=jdbc;
    }
    public record AnalysisView(UUID id,UUID sourceLogId,LocalDate targetDate,int budgetMinutes,String feedback,
        AiContracts.Provider provider,String model,AiAnalysis.Status status,AiContracts.Result result,
        AiContextService.Snapshot snapshot,UUID suggestionId,PlanSuggestion.Status suggestionStatus,int attemptCount,
        Long durationMs,Long inputTokens,Long outputTokens,String errorCode,String errorMessage,Instant createdAt){}
    public record AnalysisSummary(UUID id,UUID sourceLogId,LocalDate targetDate,AiContracts.Provider provider,String model,
        AiAnalysis.Status status,UUID suggestionId,PlanSuggestion.Status suggestionStatus,Instant createdAt){}
    public record History(List<AnalysisSummary> items,long total,int page,int size){}
    public record SuggestionView(UUID id,UUID analysisId,LocalDate targetDate,PlanSuggestion.Status status,Long version,
        List<AiContracts.ProposedTask> tasks,List<UUID> taskIds,Instant confirmedAt,boolean stale,String staleReason,AnalysisView analysis){}
    @Transactional public AnalysisView create(String key,AiContracts.Generate i){
        if(key==null||!key.matches("[A-Za-z0-9_-]{8,128}"))throw new ApiException(400,"INVALID_REQUEST_KEY","请提供合法的 Idempotency-Key");
        settings.mutex();String hash=json.hash(json.write(i));
        var existing=analyses.findByRequestKey(key);
        if(existing.isPresent()){
            if(!existing.get().requestHash.equals(hash))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","同一个请求键不能用于不同输入");
            return analysisView(existing.get());
        }
        if(config.key(i.provider()).isBlank()||config.url(i.provider()).isBlank())throw new ApiException(422,"PROVIDER_NOT_CONFIGURED","当前提供商尚未配置服务端密钥，请先在 .env 中配置并重启后端");
        if(java.util.Objects.requireNonNullElse(jdbc.queryForObject("select count(*) from ai_work where status in ('PENDING','RUNNING')",Long.class),0L)>0||analyses.existsByStatusIn(List.of(AiAnalysis.Status.PENDING,AiAnalysis.Status.RUNNING)))throw new ApiException(409,"ANALYSIS_BUSY","已有分析正在执行，请等它完成后再提交");
        Instant since=LocalDate.now(config.zone()).atStartOfDay(config.zone()).toInstant();
        if(analyses.countByCreatedAtGreaterThanEqual(since)+java.util.Objects.requireNonNullElse(jdbc.queryForObject("select count(*) from ai_work where created_at>=?",Long.class,java.sql.Timestamp.from(since)),0L)>=config.limit())throw new ApiException(429,"DAILY_LIMIT","今日分析次数已达上限，原始记录仍可正常编辑");
        var setting=settings.findById(1).orElseThrow();var snapshot=context.build(i,setting.preferences);
        var a=new AiAnalysis();a.sourceLogId=i.sourceLogId();a.targetDate=i.targetDate();a.budgetMinutes=i.budgetMinutes();
        a.feedback=Objects.requireNonNullElse(i.feedback(),"");a.provider=i.provider();a.model=i.model();a.requestKey=key;a.requestHash=hash;
        a.inputSnapshot=json.write(snapshot);a.contextHash=json.hash(a.inputSnapshot);analyses.saveAndFlush(a);return analysisView(a);
    }
    private AnalysisView analysisView(AiAnalysis a){
        var suggestion=suggestions.findByAnalysisId(a.id).orElse(null);
        return new AnalysisView(a.id,a.sourceLogId,a.targetDate,a.budgetMinutes,a.feedback,a.provider,a.model,a.status,
            a.outputJson==null?null:json.read(a.outputJson,AiContracts.Result.class),json.read(a.inputSnapshot,AiContextService.Snapshot.class),
            suggestion==null?null:suggestion.id,suggestion==null?null:suggestion.status,a.attemptCount,a.durationMs,a.inputTokens,a.outputTokens,a.errorCode,a.errorMessage,a.createdAt);
    }
    @Transactional(readOnly=true) public AnalysisView get(UUID id){return analysisView(analyses.findById(id).orElseThrow(ApiException::missing));}
    @Transactional(readOnly=true) public History history(int page,int size){
        if(page<0||size<1||size>50)throw new ApiException(400,"INVALID_PAGE","分析历史每页最多 50 条");
        var result=analyses.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        var byAnalysis=suggestions.findByAnalysisIdIn(result.stream().map(a->a.id).toList()).stream().collect(java.util.stream.Collectors.toMap(s->s.analysisId,s->s));
        var items=result.stream().map(a->{var s=byAnalysis.get(a.id);return new AnalysisSummary(a.id,a.sourceLogId,a.targetDate,a.provider,a.model,a.status,s==null?null:s.id,s==null?null:s.status,a.createdAt);}).toList();
        return new History(items,result.getTotalElements(),page,size);
    }
    private List<AiContracts.ProposedTask> proposed(PlanSuggestion s){return json.read(s.proposedTasksJson,TASKS);}
    private List<UUID> mapped(UUID id){return jdbc.query("select task_id from suggestion_task where suggestion_id=? order by item_id",(row,index)->row.getObject(1,UUID.class),id);}
    private SuggestionView suggestionView(PlanSuggestion s){
        var a=analyses.findById(s.analysisId).orElseThrow();boolean stale=false;String reason=null;
        if(s.status==PlanSuggestion.Status.DRAFT)try{context.fresh(json.read(a.inputSnapshot,AiContextService.Snapshot.class));}catch(ApiException e){stale=true;reason=e.getMessage();}
        return new SuggestionView(s.id,s.analysisId,s.targetDate,s.status,s.version,proposed(s),mapped(s.id),s.confirmedAt,stale,reason,analysisView(a));
    }
    @Transactional(readOnly=true) public SuggestionView suggestion(UUID id){return suggestionView(suggestions.findById(id).orElseThrow(ApiException::missing));}
    private PlanSuggestion draft(UUID id,Long version){
        var s=suggestions.lock(id).orElseThrow(ApiException::missing);
        if(s.status!=PlanSuggestion.Status.DRAFT)throw new ApiException(409,"SUGGESTION_CLOSED","该建议已确认、拒绝或被新建议替代");
        ApiException.version(s.version,version);return s;
    }
    @Transactional public SuggestionView edit(UUID id,AiContracts.Edit i){
        settings.mutex();var s=draft(id,i.version());var a=analyses.findById(s.analysisId).orElseThrow();
        var snapshot=json.read(a.inputSnapshot,AiContextService.Snapshot.class);context.fresh(snapshot);validator.validate(i.tasks(),snapshot,i.targetDate());
        s.targetDate=i.targetDate();s.proposedTasksJson=json.write(i.tasks());suggestions.saveAndFlush(s);return suggestionView(s);
    }
    @Transactional public SuggestionView reject(UUID id,Long version){
        settings.mutex();var existing=suggestions.lock(id).orElseThrow(ApiException::missing);
        if(existing.status==PlanSuggestion.Status.REJECTED)return suggestionView(existing);
        var s=draft(id,version);s.status=PlanSuggestion.Status.REJECTED;suggestions.saveAndFlush(s);return suggestionView(s);
    }
    @Transactional public AiContracts.Confirmed confirm(UUID id,Long version){
        settings.mutex();var current=suggestions.lock(id).orElseThrow(ApiException::missing);
        if(current.status==PlanSuggestion.Status.CONFIRMED)return new AiContracts.Confirmed(id,mapped(id));
        var s=draft(id,version);var a=analyses.findById(s.analysisId).orElseThrow();
        var snapshot=json.read(a.inputSnapshot,AiContextService.Snapshot.class);context.fresh(snapshot);
        var proposed=proposed(s);validator.validate(proposed,snapshot,s.targetDate);
        for(var p:proposed){
            Task t=p.existingTaskId()==null?new Task():tasks.findById(p.existingTaskId()).orElseThrow(ApiException::missing);
            t.title=p.title().strip();t.detail=p.reason();t.goalId=p.goalId();t.scheduledDate=s.targetDate;
            t.estimateMinutes=p.estimatedMinutes();t.acceptanceCriteria=p.acceptanceCriteria();
            if(p.existingTaskId()==null)t.source="AI";
            tasks.saveAndFlush(t);
            jdbc.update("insert into suggestion_task(suggestion_id,item_id,task_id) values(?,?,?)",s.id,p.itemId(),t.id);
        }
        s.status=PlanSuggestion.Status.CONFIRMED;s.confirmedAt=Instant.now();suggestions.saveAndFlush(s);
        return new AiContracts.Confirmed(id,mapped(id));
    }
    @Transactional public Optional<AiAnalysis> claim(){
        settings.mutex();var next=analyses.claimNext();next.ifPresent(a->{a.status=AiAnalysis.Status.RUNNING;a.startedAt=Instant.now();analyses.saveAndFlush(a);});return next;
    }
    @Transactional public void recover(){
        settings.mutex();for(var a:analyses.findByStatus(AiAnalysis.Status.RUNNING)){
            a.status=AiAnalysis.Status.FAILED;a.errorCode="PROCESS_RESTARTED";a.errorMessage="服务重启中断了分析，可以重新提交，原始记录已保留";a.finishedAt=Instant.now();
        }
    }
    @Transactional public void expireAbandoned(){
        settings.mutex();Instant cutoff=Instant.now().minusSeconds(config.timeout()+10L);
        for(var a:analyses.findByStatus(AiAnalysis.Status.RUNNING))if(a.startedAt!=null&&a.startedAt.isBefore(cutoff)){
            a.status=AiAnalysis.Status.FAILED;a.errorCode="ANALYSIS_EXPIRED";
            a.errorMessage="分析执行已中断或超时，可以重新提交，原始记录已保留";a.finishedAt=Instant.now();
        }
    }
    @Transactional public void attempt(UUID id,int count){
        var a=analyses.lock(id).orElseThrow();if(a.status==AiAnalysis.Status.RUNNING)a.attemptCount=count;
    }
    @Transactional public void succeed(UUID id,AiContracts.Result result,long duration,Long inputTokens,Long outputTokens){
        settings.mutex();var a=analyses.lock(id).orElseThrow();if(a.status!=AiAnalysis.Status.RUNNING)return;
        for(var old:suggestions.findByStatus(PlanSuggestion.Status.DRAFT)){
            var previous=analyses.findById(old.analysisId).orElseThrow();
            if(previous.sourceLogId.equals(a.sourceLogId)&&old.targetDate.equals(a.targetDate)&&previous.contextHash.equals(a.contextHash))old.status=PlanSuggestion.Status.SUPERSEDED;
        }
        var s=new PlanSuggestion();s.analysisId=id;s.targetDate=a.targetDate;s.proposedTasksJson=json.write(result.tomorrowTasks());suggestions.saveAndFlush(s);
        a.status=AiAnalysis.Status.SUCCEEDED;a.outputJson=json.write(result);a.durationMs=duration;a.inputTokens=inputTokens;a.outputTokens=outputTokens;a.finishedAt=Instant.now();analyses.saveAndFlush(a);
    }
    @Transactional public void fail(UUID id,String code,String message,long duration,Long inputTokens,Long outputTokens){
        var a=analyses.lock(id).orElseThrow();if(a.status!=AiAnalysis.Status.RUNNING)return;
        a.status=AiAnalysis.Status.FAILED;a.errorCode=code;a.errorMessage=message;a.durationMs=duration;a.inputTokens=inputTokens;a.outputTokens=outputTokens;a.finishedAt=Instant.now();
    }
}
