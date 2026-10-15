package io.growthhub.weekly;
import io.growthhub.common.ApiException;
import io.growthhub.coach.AiJson;
import io.growthhub.studylog.*;
import io.growthhub.task.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static io.growthhub.weekly.WeeklyContracts.*;
@Service
public class WeeklyService {
    private final WeeklyPlanRepository plans;private final WeeklyReviewRepository reviews;
    private final StudyLogRepository logs;private final TaskRepository tasks;private final AiJson json;private final io.growthhub.common.BusinessClock clock;
    public WeeklyService(WeeklyPlanRepository plans,WeeklyReviewRepository reviews,StudyLogRepository logs,TaskRepository tasks,AiJson json,io.growthhub.common.BusinessClock clock){
        this.plans=plans;this.reviews=reviews;this.logs=logs;this.tasks=tasks;this.json=json;this.clock=clock;
    }
    public static LocalDate monday(LocalDate date){return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
    private void requireMonday(LocalDate date){if(!date.equals(monday(date)))throw new ApiException(400,"INVALID_WEEK_START","周起始日期必须是周一");}
    public Facts facts(LocalDate start){
        ZoneId zone=clock.zone();LocalDate end=start.plusDays(6);var page=PageRequest.of(0,501,Sort.by("id"));
        var lp=logs.findAll((r,q,b)->b.between(r.get("studyDate"),start,end),page);
        var scheduled=tasks.findAll((r,q,b)->b.between(r.get("scheduledDate"),start,end),page);
        Instant from=start.atStartOfDay(zone).toInstant(),to=start.plusDays(7).atStartOfDay(zone).toInstant();
        var completed=tasks.findAll((r,q,b)->b.and(b.equal(r.get("status"),Task.Status.DONE),b.greaterThanOrEqualTo(r.get("completedAt"),from),b.lessThan(r.get("completedAt"),to)),page);
        if(lp.getTotalElements()>500||scheduled.getTotalElements()>500||completed.getTotalElements()>500)throw new ApiException(413,"WEEK_TOO_LARGE","该周记录超过在线复盘上限，请使用数据导出查看");
        var logViews=lp.stream().map(l->new StudyLogController.View(l.id,l.studyDate,l.content,l.durationMinutes,l.achievements,l.blockers,new TreeSet<>(l.taskIds),l.version,l.activityType)).toList();
        var ids=new TreeSet<UUID>();lp.forEach(l->ids.addAll(l.taskIds));
        if(ids.size()>1000)throw new ApiException(413,"WEEK_TOO_LARGE","该周关联任务过多，请使用数据导出查看");
        var linked=tasks.findAllById(ids).stream().sorted(Comparator.comparing(t->t.id)).map(TaskController.View::of).toList();
        var active=scheduled.stream().filter(t->t.status!=Task.Status.CANCELLED).toList();
        var summary=new Summary(logViews.size(),lp.stream().mapToLong(l->l.durationMinutes).sum(),active.size(),active.stream().mapToLong(t->t.estimateMinutes).sum(),
            (int)active.stream().filter(t->t.status==Task.Status.DONE).count(),(int)completed.getTotalElements(),lp.stream().filter(l->l.activityType.equals("LEARNING")).mapToLong(l->l.durationMinutes).sum(),lp.stream().filter(l->l.activityType.equals("DEVELOPMENT")).mapToLong(l->l.durationMinutes).sum());
        var result=new Facts(start,end,zone.toString(),plans.findByWeekStart(start).map(Plan::of).orElse(null),summary,
            logViews,scheduled.stream().map(TaskController.View::of).toList(),completed.stream().map(TaskController.View::of).toList(),linked);
        if(json.write(result).length()>2000000)throw new ApiException(413,"WEEK_TOO_LARGE","该周原文过长，请使用数据导出查看");
        return result;
    }
    private Review view(WeeklyReview r,Facts current){
        return new Review(r.id,r.weekStart,r.finalContent,r.status,r.confirmedAt,r.version,!r.sourceHash.equals(json.hash(json.write(current))),json.read(r.sourceSnapshot,Facts.class),r.aiDraft==null?null:json.read(r.aiDraft,Object.class));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Overview overview(LocalDate date){
        var f=facts(monday(date));return new Overview(f,reviews.findByWeekStart(f.weekStart()).map(r->view(r,f)).orElse(null));
    }
    @Transactional(readOnly=true) public java.util.List<Plan> plans(){return plans.findAll(PageRequest.of(0,200,Sort.by(Sort.Direction.DESC,"weekStart"))).stream().map(Plan::of).toList();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Review getReview(UUID id){var r=reviews.findById(id).orElseThrow(ApiException::missing);return view(r,facts(r.weekStart));}
    @Transactional public Plan createPlan(PlanInput i){
        requireMonday(i.weekStart());if(plans.findByWeekStart(i.weekStart()).isPresent())throw new ApiException(409,"WEEK_PLAN_EXISTS","该周已有计划，请编辑现有计划");
        var p=new WeeklyPlan();p.weekStart=i.weekStart();p.theme=i.theme().strip();p.budgetMinutes=i.budgetMinutes();p.notes=i.notes();return Plan.of(plans.saveAndFlush(p));
    }
    @Transactional public Plan updatePlan(UUID id,PlanUpdate i){
        var p=plans.findById(id).orElseThrow(ApiException::missing);ApiException.version(p.version,i.version());
        p.theme=i.theme().strip();p.budgetMinutes=i.budgetMinutes();p.notes=i.notes();p.status=i.status();return Plan.of(plans.saveAndFlush(p));
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ) public Review createReview(ReviewInput i){
        requireMonday(i.weekStart());if(reviews.findByWeekStart(i.weekStart()).isPresent())throw new ApiException(409,"WEEK_REVIEW_EXISTS","该周已有复盘，请继续编辑");
        var f=facts(i.weekStart());var r=new WeeklyReview();r.weekStart=i.weekStart();r.sourceSnapshot=json.write(f);r.sourceHash=json.hash(r.sourceSnapshot);
        reviews.saveAndFlush(r);return view(r,f);
    }
    private WeeklyReview draft(UUID id,Long version){
        var r=reviews.lock(id).orElseThrow(ApiException::missing);ApiException.version(r.version,version);
        if(r.status!=WeeklyReview.Status.DRAFT)throw new ApiException(409,"REVIEW_READ_ONLY","已确认复盘只读，后续记录不会覆盖历史快照");return r;
    }
    @Transactional public Review editReview(UUID id,ReviewUpdate i){
        var r=draft(id,i.version());r.finalContent=i.finalContent();reviews.saveAndFlush(r);return view(r,facts(r.weekStart));
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ) public Review refresh(UUID id,Version i){
        var r=draft(id,i.version());var f=facts(r.weekStart);r.sourceSnapshot=json.write(f);r.sourceHash=json.hash(r.sourceSnapshot);
        reviews.saveAndFlush(r);return view(r,f);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ) public Review confirm(UUID id,Version i){
        var r=reviews.lock(id).orElseThrow(ApiException::missing);
        if(r.status==WeeklyReview.Status.CONFIRMED)return view(r,facts(r.weekStart));
        ApiException.version(r.version,i.version());
        if(r.finalContent.isBlank())throw new ApiException(400,"EMPTY_REVIEW","请先写下自己的复盘内容");
        var f=facts(r.weekStart);if(!r.sourceHash.equals(json.hash(json.write(f))))throw new ApiException(409,"STALE_WEEK_REVIEW","本周来源已变化，请刷新来源并重新核对后确认");
        r.status=WeeklyReview.Status.CONFIRMED;r.confirmedAt=Instant.now();reviews.saveAndFlush(r);return view(r,f);
    }
}
