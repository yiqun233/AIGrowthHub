package io.growthhub.feed;
import io.growthhub.common.ApiException;
import io.growthhub.task.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class FeedService {
 private final FeedItemRepository items;private final FeedCandidateRepository candidates;private final TaskRepository tasks;
 public FeedService(FeedItemRepository items,FeedCandidateRepository candidates,TaskRepository tasks){this.items=items;this.candidates=candidates;this.tasks=tasks;}
 @Transactional public FeedController.Candidate create(UUID itemId,FeedController.CandidateInput i){
  var item=items.findById(itemId).orElseThrow(ApiException::missing);var previous=candidates.findByItemId(itemId);
  if(previous.isPresent())return FeedController.Candidate.of(previous.get());
  var c=new FeedCandidate();c.itemId=itemId;c.title=i.title().strip();c.detail="来源："+item.url+"\n"+i.detail();c.acceptanceCriteria=i.acceptanceCriteria();c.estimateMinutes=i.estimateMinutes();c.scheduledDate=i.scheduledDate();return FeedController.Candidate.of(candidates.saveAndFlush(c));
 }
 @Transactional public FeedController.Candidate edit(UUID id,FeedController.CandidateEdit i){
  var c=candidates.lock(id).orElseThrow(ApiException::missing);ApiException.version(c.version,i.version());if(!c.status.equals("DRAFT"))throw new ApiException(409,"CANDIDATE_CLOSED","候选任务已处理");
  var v=i.data();c.title=v.title().strip();c.detail=v.detail();c.acceptanceCriteria=v.acceptanceCriteria();c.estimateMinutes=v.estimateMinutes();c.scheduledDate=v.scheduledDate();return FeedController.Candidate.of(candidates.saveAndFlush(c));
 }
 @Transactional public FeedController.Candidate decide(UUID id,Long version,boolean accept){
  var c=candidates.lock(id).orElseThrow(ApiException::missing);if(c.status.equals(accept?"CONFIRMED":"REJECTED"))return FeedController.Candidate.of(c);
  ApiException.version(c.version,version);if(!c.status.equals("DRAFT"))throw new ApiException(409,"CANDIDATE_CLOSED","候选任务已处理");
  if(accept){var t=new Task();t.title=c.title;t.detail=c.detail;t.acceptanceCriteria=c.acceptanceCriteria;t.estimateMinutes=c.estimateMinutes;t.scheduledDate=c.scheduledDate;t.source="FEED";tasks.saveAndFlush(t);c.taskId=t.id;}
  c.status=accept?"CONFIRMED":"REJECTED";return FeedController.Candidate.of(candidates.saveAndFlush(c));
 }
}
