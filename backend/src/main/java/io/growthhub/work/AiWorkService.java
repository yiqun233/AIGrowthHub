package io.growthhub.work;
import io.growthhub.coach.*;
import io.growthhub.common.ApiException;
import io.growthhub.weekly.*;
import io.growthhub.feed.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
@Service
public class AiWorkService {
 private final AiWorkRepository repo;private final WeeklyService weeks;private final WeeklyReviewRepository reviews;private final FeedItemRepository feeds;
 private final AiSettingRepository settings;private final AiConfiguration config;private final AiJson json;private final JdbcTemplate jdbc;
 public AiWorkService(AiWorkRepository repo,WeeklyService weeks,WeeklyReviewRepository reviews,FeedItemRepository feeds,AiSettingRepository settings,AiConfiguration config,AiJson json,JdbcTemplate jdbc){this.repo=repo;this.weeks=weeks;this.reviews=reviews;this.feeds=feeds;this.settings=settings;this.config=config;this.json=json;this.jdbc=jdbc;}
 public record Input(@NotNull Long version,@NotNull AiContracts.Provider provider,@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}") String model,@AssertTrue boolean consent){}
 public record View(UUID id,String kind,UUID targetId,AiContracts.Provider provider,String model,String status,Object snapshot,Object result,int attemptCount,Long inputTokens,Long outputTokens,Long durationMs,String errorCode,String errorMessage,Instant createdAt,String promptVersion,String schemaVersion){}
 private View view(AiWork w){return new View(w.id,w.kind,w.targetId,w.provider,w.model,w.status,json.read(w.inputSnapshot,Object.class),w.outputJson==null?null:json.read(w.outputJson,Object.class),w.attemptCount,w.inputTokens,w.outputTokens,w.durationMs,w.errorCode,w.errorMessage,w.createdAt,w.promptVersion,w.schemaVersion);}
 private Object source(String kind,UUID id,Long version){
  if(kind.equals("WEEKLY_REVIEW")){var review=weeks.getReview(id);if(version!=null)ApiException.version(review.version(),version);if(review.status()!=WeeklyReview.Status.DRAFT)throw new ApiException(409,"REVIEW_READ_ONLY","已确认复盘不能重新生成");return weeks.overview(review.weekStart()).facts();}
  var item=feeds.findById(id).orElseThrow(ApiException::missing);if(version!=null)ApiException.version(item.version,version);return FeedController.Item.of(item);
 }
 @Transactional(isolation=Isolation.REPEATABLE_READ) public View create(String kind,UUID id,String key,Input i){
  if(key==null||!key.matches("[A-Za-z0-9_-]{8,128}"))throw new ApiException(400,"INVALID_REQUEST_KEY","请提供请求幂等键");settings.mutex();
  String hash=json.hash(kind+id+json.write(i));var old=repo.findByRequestKey(key);if(old.isPresent()){if(!old.get().requestHash.equals(hash))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","同一请求键不能用于不同输入");return view(old.get());}
  if(config.key(i.provider()).isBlank()||config.url(i.provider()).isBlank())throw new ApiException(422,"PROVIDER_NOT_CONFIGURED","请先配置所选提供商");
  if(jdbc.queryForObject("select (select count(*) from ai_analysis where status in ('PENDING','RUNNING'))+(select count(*) from ai_work where status in ('PENDING','RUNNING'))",Long.class)>0)throw new ApiException(409,"ANALYSIS_BUSY","已有 AI 请求正在执行");
  var since=LocalDate.now(config.zone()).atStartOfDay(config.zone()).toInstant();
  if(jdbc.queryForObject("select (select count(*) from ai_analysis where created_at>=?)+(select count(*) from ai_work where created_at>=?)",Long.class,java.sql.Timestamp.from(since),java.sql.Timestamp.from(since))>=config.limit())throw new ApiException(429,"DAILY_LIMIT","今日 AI 分析次数已达上限");
  String snapshot=json.write(source(kind,id,i.version()));if(snapshot.length()>60000)throw new ApiException(413,"CONTEXT_TOO_LARGE","来源过长，请整理后再生成；原文仍保留");
  var w=new AiWork();w.kind=kind;w.promptVersion=kind.equals("WEEKLY_REVIEW")?"weekly-v1":"feed-v1";w.targetId=id;w.provider=i.provider();w.model=i.model();w.requestKey=key;w.requestHash=hash;w.inputSnapshot=snapshot;w.sourceHash=json.hash(snapshot);repo.saveAndFlush(w);return view(w);
 }
 @Transactional(readOnly=true) public View get(UUID id){return view(repo.findById(id).orElseThrow(ApiException::missing));}
 @Transactional(readOnly=true) public List<View> history(String kind,UUID id){return repo.findTop20ByKindAndTargetIdOrderByCreatedAtDesc(kind,id).stream().map(this::view).toList();}
 @Transactional public Optional<AiWork> claim(){settings.mutex();var w=repo.findFirstByStatusOrderByCreatedAtAsc("PENDING");w.ifPresent(v->{v.status="RUNNING";v.startedAt=Instant.now();repo.saveAndFlush(v);});return w;}
 @Transactional public void recover(){settings.mutex();for(var w:repo.findByStatus("RUNNING")){w.status="FAILED";w.errorCode="PROCESS_RESTARTED";w.errorMessage="服务重启中断了请求，来源和文字仍保留";w.finishedAt=Instant.now();}}
 @Transactional public void expire(){settings.mutex();for(var w:repo.findByStatus("RUNNING"))if(w.startedAt!=null&&w.startedAt.isBefore(Instant.now().minusSeconds(config.timeout()+10L))){w.status="FAILED";w.errorCode="ANALYSIS_EXPIRED";w.errorMessage="请求已中断或超时，可重新生成";w.finishedAt=Instant.now();}}
 @Transactional public void attempt(UUID id,int count){var w=repo.lock(id).orElseThrow();if(w.status.equals("RUNNING"))w.attemptCount=count;}
 @Transactional public void finish(UUID id,String output,String code,String message,long duration,Long input,Long generated){var w=repo.lock(id).orElseThrow();if(!w.status.equals("RUNNING"))return;w.status=output==null?"FAILED":"SUCCEEDED";w.outputJson=output;w.errorCode=code;w.errorMessage=message;w.durationMs=duration;w.inputTokens=input;w.outputTokens=generated;w.finishedAt=Instant.now();}
 public String validate(String raw,AiWork w){
  try{
   var n=json.tree(raw);if(!n.isObject()||!n.propertyNames().equals(Set.of("summary","evidence","nextSteps","limitations")))throw new IllegalArgumentException();
   for(String key:List.of("summary","limitations"))if(!n.path(key).isString()||n.path(key).stringValue().isBlank()||n.path(key).stringValue().length()>(key.equals("summary")?5000:2000))throw new IllegalArgumentException();
   if(!n.path("nextSteps").isArray()||n.path("nextSteps").size()>5)throw new IllegalArgumentException();for(var t:n.path("nextSteps"))if(!t.isString()||t.stringValue().isBlank()||t.stringValue().length()>2000)throw new IllegalArgumentException();
   Map<String,String> refs=new HashMap<>();var input=json.tree(w.inputSnapshot);
   if(w.kind.equals("FEED_SUMMARY"))refs.put(input.path("id").asString(""),input.path("title").asString("")+"\n"+input.path("content").asString(""));
   else{
    for(var l:input.path("logs"))refs.put(l.path("id").asString(""),l.path("content").asString("")+"\n"+l.path("achievements").asString("")+"\n"+l.path("blockers").asString(""));
    for(String key:List.of("scheduledTasks","completedDuringWeek","linkedTasks"))for(var t:input.path(key))refs.put(t.path("id").asString(""),t.path("title").asString("")+"\n"+t.path("detail").asString("")+"\n"+t.path("acceptanceCriteria").asString(""));
   }
   if(!n.path("evidence").isArray()||n.path("evidence").size()>20||!refs.isEmpty()&&n.path("evidence").isEmpty())throw new IllegalArgumentException();
   for(var e:n.path("evidence")){if(!e.isObject()||!e.propertyNames().equals(Set.of("sourceId","quote"))||!e.path("sourceId").isString()||!e.path("quote").isString())throw new IllegalArgumentException();String id=e.path("sourceId").stringValue(),quote=e.path("quote").stringValue();if(quote.isBlank()||quote.length()>1000||!refs.containsKey(id)||!refs.get(id).contains(quote))throw new IllegalArgumentException();}
   return json.write(json.read(raw,Object.class));
  }catch(RuntimeException ex){throw new ApiException(422,"INVALID_AI_OUTPUT","AI 输出结构或证据不正确，请使用真实来源原文");}
 }
 @Transactional(isolation=Isolation.REPEATABLE_READ) public WeeklyContracts.Review apply(UUID reviewId,UUID workId,Long version){
  var r=reviews.lock(reviewId).orElseThrow(ApiException::missing);ApiException.version(r.version,version);if(r.status!=WeeklyReview.Status.DRAFT)throw new ApiException(409,"REVIEW_READ_ONLY","已确认复盘只读");
  var w=repo.findById(workId).orElseThrow(ApiException::missing);if(!w.kind.equals("WEEKLY_REVIEW")||!w.targetId.equals(reviewId)||!w.status.equals("SUCCEEDED"))throw new ApiException(400,"INVALID_AI_WORK","请选择这份复盘已成功的 AI 草稿");
  if(!json.hash(json.write(source(w.kind,w.targetId,null))).equals(w.sourceHash))throw new ApiException(409,"STALE_CONTEXT","周来源已变化，请重新生成草稿");
  r.aiDraft=w.outputJson;r.sourceSnapshot=w.inputSnapshot;r.sourceHash=w.sourceHash;var n=json.tree(w.outputJson);var content=new StringBuilder(n.path("summary").asString(""));content.append("\n\n下周调整：\n");for(var next:n.path("nextSteps"))content.append("- ").append(next.stringValue()).append('\n');content.append("\n局限：").append(n.path("limitations").asString(""));r.finalContent=content.toString();reviews.saveAndFlush(r);return weeks.getReview(reviewId);
 }
}
