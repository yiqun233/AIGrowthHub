package io.growthhub.feed;
import io.growthhub.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class FeedController {
 private final FeedCollector collector;private final FeedItemRepository items;private final FeedCandidateRepository candidates;private final FeedService service;
 public FeedController(FeedCollector collector,FeedItemRepository items,FeedCandidateRepository candidates,FeedService service){this.collector=collector;this.items=items;this.candidates=candidates;this.service=service;}
 public record Item(UUID id,String sourceId,String title,String url,String content,Instant publishedAt,boolean favorite,Long version){public static Item of(FeedItem i){return new Item(i.id,i.sourceId,i.title,i.url,i.content,i.publishedAt,i.favorite,i.version);}}
 public record PageView(List<Item> items,long total,int page,int size){}
 public record Favorite(@NotNull Long version,@NotNull Boolean favorite){}
 public record Version(@NotNull Long version){}
 public record CandidateInput(@NotBlank @Size(max=200) String title,@NotNull @Size(max=20000) String detail,@NotBlank @Size(max=5000) String acceptanceCriteria,@NotNull @Min(1) @Max(1440) Integer estimateMinutes,@NotNull LocalDate scheduledDate){}
 public record CandidateEdit(@NotNull Long version,@NotNull @Valid CandidateInput data){}
 public record Candidate(UUID id,UUID itemId,String title,String detail,String acceptanceCriteria,int estimateMinutes,LocalDate scheduledDate,String status,UUID taskId,Long version){static Candidate of(FeedCandidate c){return new Candidate(c.id,c.itemId,c.title,c.detail,c.acceptanceCriteria,c.estimateMinutes,c.scheduledDate,c.status,c.taskId,c.version);}}
 @GetMapping("/feed/sources") public List<Map<String,Object>> sources(){return collector.sources();}
 @PostMapping("/feed/refresh") public List<Map<String,Object>> refresh(){return collector.refresh();}
 @GetMapping("/feed/items") public PageView items(@RequestParam(required=false) String source,@RequestParam(defaultValue="false") boolean favorite,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  if(page<0||size<1||size>100)throw new ApiException(400,"INVALID_PAGE","每页最多 100 条");Specification<FeedItem> spec=(r,q,b)->b.conjunction();
  if(source!=null)spec=spec.and((r,q,b)->b.equal(r.get("sourceId"),source));if(favorite)spec=spec.and((r,q,b)->b.isTrue(r.get("favorite")));
  var p=items.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"publishedAt","id")));return new PageView(p.stream().map(Item::of).toList(),p.getTotalElements(),page,size);
 }
 @PatchMapping("/feed/items/{id}") @Transactional public Item favorite(@PathVariable UUID id,@Valid @RequestBody Favorite i){var item=items.findById(id).orElseThrow(ApiException::missing);ApiException.version(item.version,i.version());item.favorite=i.favorite();return Item.of(items.saveAndFlush(item));}
 @GetMapping("/feed/candidates") public List<Candidate> candidates(){return candidates.findAll(PageRequest.of(0,200,Sort.by(Sort.Direction.DESC,"createdAt"))).stream().map(Candidate::of).toList();}
 @PostMapping("/feed/items/{id}/candidate") public Candidate create(@PathVariable UUID id,@Valid @RequestBody CandidateInput i){return service.create(id,i);}
 @PatchMapping("/feed/candidates/{id}") public Candidate edit(@PathVariable UUID id,@Valid @RequestBody CandidateEdit i){return service.edit(id,i);}
 @PostMapping("/feed/candidates/{id}/confirm") public Candidate confirm(@PathVariable UUID id,@Valid @RequestBody Version i){return service.decide(id,i.version(),true);}
 @PostMapping("/feed/candidates/{id}/reject") public Candidate reject(@PathVariable UUID id,@Valid @RequestBody Version i){return service.decide(id,i.version(),false);}
}
