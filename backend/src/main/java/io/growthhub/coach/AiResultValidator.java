package io.growthhub.coach;
import io.growthhub.common.ApiException;
import io.growthhub.goal.*;
import io.growthhub.task.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
@Component
public class AiResultValidator {
    private final AiJson json;private final GoalRepository goals;private final TaskRepository tasks;
    public AiResultValidator(AiJson json,GoalRepository goals,TaskRepository tasks){this.json=json;this.goals=goals;this.tasks=tasks;}
    public AiContracts.Result parse(String raw,AiContextService.Snapshot s){
        try{
            var n=json.tree(raw);shape(n,Set.of("summary","learnedTopics","evidence","openQuestions","masteryAssessment","tomorrowTasks","planAdjustmentReason"));
            String summary=text(n,"summary",6000),mastery=text(n,"masteryAssessment",3000),reason=text(n,"planAdjustmentReason",3000);
            var topics=strings(n.get("learnedTopics"));var questions=strings(n.get("openQuestions"));
            var logMap=new HashMap<UUID,String>();logMap.put(s.source().id(),s.source().content()+"\n"+s.source().achievements()+"\n"+s.source().blockers());
            s.recentLogs().forEach(l->logMap.put(l.id(),l.content()+"\n"+l.achievements()+"\n"+l.blockers()));
            var evidence=new ArrayList<AiContracts.Evidence>();var e=n.get("evidence");array(e,20);
            for(var entry:e){shape(entry,Set.of("logId","quote"));UUID id=UUID.fromString(text(entry,"logId",40));String quote=text(entry,"quote",2000);
                if(!logMap.containsKey(id)||!logMap.get(id).contains(quote))invalid("分析引用了不存在的证据");evidence.add(new AiContracts.Evidence(id,quote));}
            if(evidence.isEmpty())invalid("分析必须引用至少一条真实记录");
            var proposals=new ArrayList<AiContracts.ProposedTask>();array(n.get("tomorrowTasks"),3);
            for(var task:n.get("tomorrowTasks")){
                shape(task,Set.of("title","reason","estimatedMinutes","acceptanceCriteria","goalId","existingTaskId"));
                var minutes=task.get("estimatedMinutes");if(!minutes.isIntegralNumber()||!minutes.canConvertToInt())invalid("任务时长必须为整数");
                proposals.add(new AiContracts.ProposedTask(UUID.randomUUID(),text(task,"title",200),text(task,"reason",2000),minutes.intValue(),text(task,"acceptanceCriteria",5000),id(task.get("goalId")),id(task.get("existingTaskId"))));
            }
            validate(proposals,s,s.targetDate());
            return new AiContracts.Result(summary,topics,evidence,questions,mastery,proposals,reason);
        }catch(ApiException e){throw e;}catch(RuntimeException e){throw new ApiException(422,"INVALID_AI_OUTPUT","模型返回的 JSON 结构或字段不符合约定");}
    }
    private void shape(JsonNode n,Set<String> expected){
        if(n==null||!n.isObject())invalid("JSON 对象结构不正确");var keys=new HashSet<String>();n.propertyNames().forEach(keys::add);if(!keys.equals(expected))invalid("JSON 缺少字段或含未知字段");
    }
    private String text(JsonNode n,String key,int max){var v=n.get(key);if(v==null||!v.isString()||v.stringValue().isBlank()||v.stringValue().length()>max)invalid("文本字段不符合要求");return v.stringValue();}
    private void array(JsonNode n,int max){if(n==null||!n.isArray()||n.size()>max)invalid("数组数量不符合要求");}
    private List<String> strings(JsonNode n){array(n,20);var result=new ArrayList<String>();for(var x:n){if(!x.isString()||x.stringValue().isBlank()||x.stringValue().length()>1000)invalid("主题或问题字段不正确");result.add(x.stringValue());}return result;}
    private UUID id(JsonNode n){if(n.isNull())return null;if(!n.isString())invalid("关联 ID 必须是字符串或 null");return UUID.fromString(n.stringValue());}
    private void invalid(String message){throw new ApiException(422,"INVALID_AI_OUTPUT",message);}
    public void validate(List<AiContracts.ProposedTask> proposals,AiContextService.Snapshot s,java.time.LocalDate target){
        if(proposals.size()>3)throw new ApiException(422,"INVALID_AI_OUTPUT","每天最多三项建议");
        var goalIds=new HashSet<UUID>();s.goals().forEach(g->goalIds.add(g.id()));
        var sourceTasks=new HashSet<UUID>();s.openTasks().forEach(t->sourceTasks.add(t.id()));s.targetTasks().forEach(t->sourceTasks.add(t.id()));
        var itemIds=new HashSet<UUID>();var existing=new HashSet<UUID>();var titles=new HashSet<String>();long minutes=0;
        for(var p:proposals){
            if(p.itemId()==null||!itemIds.add(p.itemId())||p.title()==null||p.title().isBlank()||!titles.add(p.title().strip().toLowerCase(Locale.ROOT)))invalid("建议条目或标题重复");
            if(p.estimatedMinutes()==null||p.estimatedMinutes()<1||p.estimatedMinutes()>1440)invalid("任务时长不合法");
            if(p.goalId()!=null && (!goalIds.contains(p.goalId())||!goals.findById(p.goalId()).map(g->g.status==Goal.Status.ACTIVE).orElse(false)))invalid("建议关联的目标不在输入范围内或已归档");
            if(p.existingTaskId()!=null){
                if(!sourceTasks.contains(p.existingTaskId())||!existing.add(p.existingTaskId()))invalid("已有任务关联无效或重复");
                var t=tasks.findById(p.existingTaskId()).orElseThrow(()->new ApiException(422,"INVALID_AI_OUTPUT","已有任务不存在"));
                if(t.status==Task.Status.DONE||t.status==Task.Status.CANCELLED)invalid("不能重新安排已完成或已取消的任务");
            }
            minutes+=p.estimatedMinutes();
        }
        var retained=tasks.findAll((root,q,b)->b.and(b.equal(root.get("scheduledDate"),target),b.notEqual(root.get("status"),Task.Status.CANCELLED)));
        minutes+=retained.stream().filter(t->!existing.contains(t.id)).mapToLong(t->t.estimateMinutes).sum();
        if(minutes>s.budgetMinutes())throw new ApiException(422,"BUDGET_EXCEEDED","建议与目标日已有任务的总时长超过预算，请减少任务或重新生成");
    }
}
