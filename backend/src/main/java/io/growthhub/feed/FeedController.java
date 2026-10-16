package io.growthhub.feed;
import io.growthhub.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class FeedController {
 private final FeedCollector collector;private final FeedItemRepository items;private final FeedCandidateRepository candidates;private final FeedService service;private final FeedPlatformRegistry platforms;private final FeedCookieCrypto cookieCrypto;private final JdbcTemplate jdbc;
 public FeedController(FeedCollector collector,FeedItemRepository items,FeedCandidateRepository candidates,FeedService service,FeedPlatformRegistry platforms,FeedCookieCrypto cookieCrypto,JdbcTemplate jdbc){this.collector=collector;this.items=items;this.candidates=candidates;this.service=service;this.platforms=platforms;this.cookieCrypto=cookieCrypto;this.jdbc=jdbc;}
 public record Item(UUID id,String sourceId,String title,String url,String content,Instant publishedAt,boolean favorite,Long version){public static Item of(FeedItem i){return new Item(i.id,i.sourceId,i.title,i.url,i.content,i.publishedAt,i.favorite,i.version);}}
 public record PageView(List<Item> items,long total,int page,int size){}
 public record Favorite(@NotNull Long version,@NotNull Boolean favorite){}
 public record Version(@NotNull Long version){}
 public record CandidateInput(@NotBlank @Size(max=200) String title,@NotNull @Size(max=20000) String detail,@NotBlank @Size(max=5000) String acceptanceCriteria,@NotNull @Min(1) @Max(1440) Integer estimateMinutes,@NotNull LocalDate scheduledDate){}
 public record CandidateEdit(@NotNull Long version,@NotNull @Valid CandidateInput data){}
 public record SubscriptionInput(@NotBlank String platformId,@Size(max=200) String label,@NotBlank @Size(max=2000) String profileUrl){}
 public record CookieInput(@NotBlank @Size(max=8192) String cookie){}
 public record Candidate(UUID id,UUID itemId,String title,String detail,String acceptanceCriteria,int estimateMinutes,LocalDate scheduledDate,String status,UUID taskId,Long version){static Candidate of(FeedCandidate c){return new Candidate(c.id,c.itemId,c.title,c.detail,c.acceptanceCriteria,c.estimateMinutes,c.scheduledDate,c.status,c.taskId,c.version);}}
 @GetMapping("/feed/sources") public List<Map<String,Object>> sources(){return collector.sources();}
 @GetMapping("/feed/platforms") public List<FeedPlatformRegistry.Platform> platforms(){return platforms.platforms();}
 @PostMapping("/feed/sources") @Transactional public Map<String,Object> subscribe(@Valid @RequestBody SubscriptionInput input){
  var source=platforms.createSource(input.platformId(),input.label(),input.profileUrl());String id=UUID.randomUUID().toString().replace("-","").substring(0,30);
  try{jdbc.update("insert into feed_source(id,label,url,platform_id,adapter_type,profile_url,enabled,created_at) values(?,?,?,?,?,?,true,now())",id,source.label(),source.fetchUrl(),source.platformId(),source.adapterType(),source.profileUrl());}
  catch(org.springframework.dao.DuplicateKeyException duplicate){throw new ApiException(409,"FEED_ALREADY_SUBSCRIBED","这个主页已经订阅");}
  return jdbc.queryForMap("select s.id,s.label,s.url,s.platform_id,s.adapter_type,s.profile_url,s.enabled,s.last_attempt_at,s.last_success_at,s.error_code,s.item_count,exists(select 1 from feed_source_secret c where c.source_id=s.id) as cookie_configured from feed_source s where s.id=?",id);
 }
 @PutMapping("/feed/sources/{id}/cookie") @Transactional public Map<String,Object> saveCookie(@PathVariable String id,@Valid @RequestBody CookieInput input){
  var source=jdbc.query("select adapter_type from feed_source where id=? and enabled=true",(rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(ApiException::missing);
  if(!"scraper".equals(source))throw new ApiException(400,"FEED_COOKIE_NOT_SUPPORTED","这个来源不使用 GrowthHub Cookie");
  String rawCookie=input.cookie().strip();if(rawCookie.chars().anyMatch(Character::isISOControl))throw new ApiException(400,"FEED_COOKIE_INVALID","Cookie 不能包含换行或控制字符");
  String encrypted=cookieCrypto.encrypt(rawCookie);jdbc.update("insert into feed_source_secret(source_id,encrypted_cookie,updated_at) values(?,?,now()) on conflict(source_id) do update set encrypted_cookie=excluded.encrypted_cookie,updated_at=now()",id,encrypted);
  return Map.of("id",id,"cookieConfigured",true);
 }
 @DeleteMapping("/feed/sources/{id}/cookie") @Transactional public Map<String,Object> clearCookie(@PathVariable String id){jdbc.update("delete from feed_source_secret where source_id=?",id);return Map.of("id",id,"cookieConfigured",false);}
 @DeleteMapping("/feed/sources/{id}") @Transactional public Map<String,Object> unsubscribe(@PathVariable String id){int changed=jdbc.update("update feed_source set enabled=false where id=? and profile_url is not null",id);if(changed==0)throw ApiException.missing();jdbc.update("delete from feed_source_secret where source_id=?",id);return Map.of("id",id,"enabled",false);}
 @PostMapping("/feed/refresh") public List<Map<String,Object>> refresh(){return collector.refresh();}
 @GetMapping("/feed/items") public PageView items(@RequestParam(required=false) String source,@RequestParam(defaultValue="false") boolean favorite,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  if(page<0||size<1||size>100)throw new ApiException(400,"INVALID_PAGE","每页最多 100 条");var enabledIds=jdbc.queryForList("select id from feed_source where enabled=true",String.class);Specification<FeedItem> spec=(r,q,b)->enabledIds.isEmpty()?b.disjunction():r.get("sourceId").in(enabledIds);
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
