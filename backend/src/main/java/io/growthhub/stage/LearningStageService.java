package io.growthhub.stage;

import io.growthhub.achievement.*;
import io.growthhub.common.*;
import io.growthhub.coach.AiJson;
import io.growthhub.studylog.*;
import io.growthhub.task.*;
import io.growthhub.weekly.*;
import io.growthhub.work.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static io.growthhub.stage.StageContracts.*;

@Service
public class LearningStageService {
 private final LearningStageRepository stages;private final LearningStageReviewRepository reviews;private final StudyLogRepository logs;
 private final TaskRepository tasks;private final AchievementRepository achievements;private final WeeklyPlanRepository plans;private final WeeklyReviewRepository weeklyReviews;
 private final AiJson json;private final BusinessClock clock;
 private final AiWorkRepository works;
 public LearningStageService(LearningStageRepository stages,LearningStageReviewRepository reviews,StudyLogRepository logs,TaskRepository tasks,
   AchievementRepository achievements,WeeklyPlanRepository plans,WeeklyReviewRepository weeklyReviews,AiJson json,BusinessClock clock,AiWorkRepository works){
  this.stages=stages;this.reviews=reviews;this.logs=logs;this.tasks=tasks;this.achievements=achievements;this.plans=plans;this.weeklyReviews=weeklyReviews;this.json=json;this.clock=clock;this.works=works;
 }
 public static void requireExisting(LearningStageRepository repo,UUID id){if(id!=null&&!repo.existsById(id))throw new ApiException(400,"INVALID_STAGE","学习阶段不存在");}
 private Stage view(LearningStage s){return Stage.of(s);}
 private Review reviewView(LearningStageReview r,Facts current){return new Review(r.id,r.stageId,r.finalContent,r.status,stages.findById(r.stageId).orElseThrow(ApiException::missing).outcome,r.confirmedAt,r.version,r.status==LearningStageReview.Status.DRAFT&&!r.sourceHash.equals(json.hash(json.write(current))),json.read(r.sourceSnapshot,Facts.class),r.aiDraft==null?null:json.read(r.aiDraft,Object.class));}
 @Transactional(readOnly=true) public List<Stage> list(){return stages.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();}
 @Transactional public Stage create(Input i){
  if(stages.existsByStatus(LearningStage.Status.ACTIVE))throw new ApiException(409,"ACTIVE_STAGE_EXISTS","请先暂停或结束当前学习主题，再开始新主题");
  if(i.targetDate()!=null&&i.targetDate().isBefore(i.startDate()))throw new ApiException(400,"INVALID_STAGE_DATES","目标日期不能早于开始日期");
  var s=new LearningStage();s.title=i.title().strip();s.desiredOutcome=i.desiredOutcome().strip();s.acceptanceCriteria=i.acceptanceCriteria().strip();s.startDate=i.startDate();s.targetDate=i.targetDate();return view(stages.saveAndFlush(s));
 }
 @Transactional public Stage update(UUID id,Update i){var s=stages.findById(id).orElseThrow(ApiException::missing);ApiException.version(s.version,i.version());
  if(s.status==LearningStage.Status.CLOSED)throw new ApiException(409,"STAGE_CLOSED","已结束的学习主题不能编辑");if(i.targetDate()!=null&&i.targetDate().isBefore(i.startDate()))throw new ApiException(400,"INVALID_STAGE_DATES","目标日期不能早于开始日期");
  s.title=i.title().strip();s.desiredOutcome=i.desiredOutcome().strip();s.acceptanceCriteria=i.acceptanceCriteria().strip();s.startDate=i.startDate();s.targetDate=i.targetDate();return view(stages.saveAndFlush(s));}
 @Transactional public Stage status(UUID id,Version i,LearningStage.Status status){var s=stages.findById(id).orElseThrow(ApiException::missing);ApiException.version(s.version,i.version());
  if(s.status==LearningStage.Status.CLOSED)throw new ApiException(409,"STAGE_CLOSED","已结束的学习主题不能重新开启");
  if(status==LearningStage.Status.ACTIVE&&stages.existsByStatus(LearningStage.Status.ACTIVE)&&s.status!=LearningStage.Status.ACTIVE)throw new ApiException(409,"ACTIVE_STAGE_EXISTS","请先暂停当前主题");
  if(status==LearningStage.Status.CLOSED)throw new ApiException(400,"REVIEW_REQUIRED","请完成阶段复盘并选择结果后再结束");s.status=status;return view(stages.saveAndFlush(s));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Facts facts(UUID id){
  var s=stages.findById(id).orElseThrow(ApiException::missing);var logRows=logs.findByLearningStageIdOrderByStudyDateAscCreatedAtAsc(id);var taskRows=tasks.findByLearningStageIdOrderByScheduledDateAscCreatedAtAsc(id);
  var achievementRows=achievements.findByLearningStageIdOrderByAchievementDateAsc(id);var planRows=plans.findByLearningStageIdOrderByWeekStartAsc(id);
  if(logRows.size()>2000||taskRows.size()>2000||achievementRows.size()>1000||planRows.size()>500)throw new ApiException(413,"STAGE_TOO_LARGE","该学习阶段关联数据过多，请使用数据导出查看");
  var weeklyRows=planRows.isEmpty()?List.<StageContracts.WeeklyReview>of():weeklyReviews.findByWeekStartIn(planRows.stream().map(p->p.weekStart).toList()).stream().map(r->new StageContracts.WeeklyReview(r.id,r.weekStart,r.status.name(),r.finalContent)).toList();
  var result=new Facts(Stage.of(s),clock.zone().toString(),new Summary(logRows.size(),logRows.stream().mapToLong(l->l.durationMinutes).sum(),taskRows.size(),(int)taskRows.stream().filter(t->t.status==Task.Status.DONE).count(),achievementRows.size(),weeklyRows.size()),
   logRows.stream().map(StudyLogController.View::of).toList(),taskRows.stream().map(TaskController.View::of).toList(),achievementRows.stream().map(AchievementController.View::of).toList(),planRows.stream().map(WeeklyContracts.Plan::of).toList(),weeklyRows);
  if(json.write(result).length()>2000000)throw new ApiException(413,"STAGE_TOO_LARGE","该阶段原文超过在线复盘上限，请使用数据导出查看");return result;
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Detail detail(UUID id){var s=stages.findById(id).orElseThrow(ApiException::missing);var f=facts(id);var r=reviews.findByStageId(id).map(x->reviewView(x,f)).orElse(null);return new Detail(Stage.of(s),f,r);}
 @Transactional(isolation=Isolation.REPEATABLE_READ) public Review createReview(UUID stageId){var s=stages.findById(stageId).orElseThrow(ApiException::missing);if(s.status==LearningStage.Status.CLOSED)throw new ApiException(409,"STAGE_CLOSED","该阶段已结束");if(reviews.findByStageId(stageId).isPresent())throw new ApiException(409,"STAGE_REVIEW_EXISTS","该阶段已有复盘，请继续编辑");var f=facts(stageId);var r=new LearningStageReview();r.stageId=stageId;r.sourceSnapshot=json.write(f);r.sourceHash=json.hash(r.sourceSnapshot);reviews.saveAndFlush(r);return reviewView(r,f);}
 private LearningStageReview draft(UUID id,Long version){var r=reviews.lock(id).orElseThrow(ApiException::missing);ApiException.version(r.version,version);if(r.status!=LearningStageReview.Status.DRAFT)throw new ApiException(409,"STAGE_REVIEW_READ_ONLY","已确认阶段复盘只读");return r;}
 @Transactional public Review editReview(UUID id,ReviewUpdate i){var r=draft(id,i.version());r.finalContent=i.finalContent();reviews.saveAndFlush(r);return reviewView(r,facts(r.stageId));}
 @Transactional(isolation=Isolation.REPEATABLE_READ) public Review refresh(UUID id,Version i){var r=draft(id,i.version());var f=facts(r.stageId);r.sourceSnapshot=json.write(f);r.sourceHash=json.hash(r.sourceSnapshot);reviews.saveAndFlush(r);return reviewView(r,f);}
 @Transactional(isolation=Isolation.REPEATABLE_READ) public Review confirm(UUID id,OutcomeInput i){var r=reviews.lock(id).orElseThrow(ApiException::missing);if(r.status==LearningStageReview.Status.CONFIRMED)return reviewView(r,facts(r.stageId));ApiException.version(r.version,i.version());if(r.finalContent.isBlank())throw new ApiException(400,"EMPTY_STAGE_REVIEW","请先写下阶段复盘内容");var f=facts(r.stageId);if(!r.sourceHash.equals(json.hash(json.write(f))))throw new ApiException(409,"STALE_STAGE_REVIEW","阶段来源已变化，请刷新并核对后确认");var s=stages.findById(r.stageId).orElseThrow(ApiException::missing);s.outcome=i.outcome();s.status=LearningStage.Status.CLOSED;s.version++;s.updatedAt=Instant.now();r.status=LearningStageReview.Status.CONFIRMED;r.confirmedAt=Instant.now();reviews.saveAndFlush(r);stages.saveAndFlush(s);return reviewView(r,f);}
 @Transactional(isolation=Isolation.REPEATABLE_READ) public Review applyAi(UUID reviewId,UUID workId,Long version){
  var r=draft(reviewId,version);var f=facts(r.stageId);var w=works.findById(workId).orElseThrow(ApiException::missing);
  if(!w.kind.equals("STAGE_REVIEW")||!w.targetId.equals(reviewId)||!w.status.equals("SUCCEEDED"))throw new ApiException(400,"INVALID_AI_WORK","请选择这份阶段复盘已成功的 AI 草稿");
  String currentHash=json.hash(json.write(f));if(!currentHash.equals(w.sourceHash)||!r.sourceHash.equals(w.sourceHash))throw new ApiException(409,"STALE_CONTEXT","阶段来源已变化，请刷新后重新生成草稿");
  var jsonTree=json.tree(w.outputJson);r.aiDraft=w.outputJson;r.sourceSnapshot=w.inputSnapshot;r.sourceHash=w.sourceHash;
  var content=new StringBuilder(jsonTree.path("summary").asString(""));content.append("\n\n下一步建议：\n");for(var next:jsonTree.path("nextSteps"))content.append("- ").append(next.stringValue()).append('\n');content.append("\n局限：").append(jsonTree.path("limitations").asString(""));r.finalContent=content.toString();reviews.saveAndFlush(r);return reviewView(r,f);
 }
 @Transactional(readOnly=true) public Review getReview(UUID id){var r=reviews.findById(id).orElseThrow(ApiException::missing);return reviewView(r,facts(r.stageId));}
}
