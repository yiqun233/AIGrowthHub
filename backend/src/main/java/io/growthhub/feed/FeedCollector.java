package io.growthhub.feed;
import io.growthhub.coach.AiJson;
import io.growthhub.common.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.w3c.dom.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
@Component
public class FeedCollector {
 private final JdbcTemplate jdbc;private final AiJson json;private final Environment env;private final FeedPlatformRegistry platforms;private final FeedCookieCrypto cookieCrypto;
 private final AtomicBoolean busy=new AtomicBoolean();private Instant last=Instant.EPOCH;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public FeedCollector(JdbcTemplate jdbc,AiJson json,Environment env,FeedPlatformRegistry platforms,FeedCookieCrypto cookieCrypto){this.jdbc=jdbc;this.json=json;this.env=env;this.platforms=platforms;this.cookieCrypto=cookieCrypto;}
 @Scheduled(fixedDelayString="${FEED_REFRESH_MS:900000}") public void scheduled(){if(env.getProperty("FEED_AUTO_REFRESH",Boolean.class,false))try{refresh();}catch(ApiException ignored){}}
 public List<Map<String,Object>> sources(){return jdbc.queryForList("select s.id,s.label,s.url,s.platform_id,s.adapter_type,s.profile_url,s.enabled,s.last_attempt_at,s.last_success_at,s.error_code,s.item_count,exists(select 1 from feed_source_secret c where c.source_id=s.id) as cookie_configured from feed_source s order by s.platform_id,s.label");}
 public List<Map<String,Object>> refresh(){
  if(!busy.compareAndSet(false,true))throw new ApiException(409,"FEED_BUSY","资讯采集正在进行");
  try{
   if(Instant.now().isBefore(last.plusSeconds(10)))throw new ApiException(429,"FEED_COOLDOWN","请稍后再刷新资讯");last=Instant.now();
   for(var row:jdbc.queryForList("select id,label,url,platform_id,adapter_type,profile_url,enabled,last_attempt_at,last_success_at,error_code,item_count from feed_source where enabled=true order by id")){
    String id=(String)row.get("id");String url=env.getProperty("FEED_"+id.toUpperCase(Locale.ROOT).replace('-','_')+"_URL",(String)row.get("url"));
    jdbc.update("update feed_source set last_attempt_at=now() where id=?",id);
    try{
     URI uri=URI.create(url);String platformId=(String)row.get("platform_id");String adapterType=(String)row.get("adapter_type");
     boolean localTest="http".equalsIgnoreCase(uri.getScheme())&&Set.of("127.0.0.1","localhost").contains(uri.getHost());
     if(uri.getUserInfo()!=null||uri.getHost()==null||!(platforms.allowedFetchHost(platformId,uri)||localTest))throw new IOException();
     var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("User-Agent","Mozilla/5.0 (compatible; GrowthHub/0.1 feed reader)");
     if("scraper".equals(adapterType)){String cookie=jdbc.query("select encrypted_cookie from feed_source_secret where source_id=?",rs->rs.next()?cookieCrypto.decrypt(rs.getString(1)):null,id);if(cookie!=null)request.header("Cookie",cookie);}
     var future=http.sendAsync(request.GET().build(),info->new io.growthhub.common.BoundedBody(2_000_000));
     HttpResponse<byte[]> res;try{res=future.get(12,java.util.concurrent.TimeUnit.SECONDS);}finally{if(!future.isDone())future.cancel(true);}
     byte[] bytes=res.body();if(res.statusCode()!=200)throw new IOException();
     if("scraper".equals(adapterType))collectHtml(id,platformId,url,bytes);else collectFeed(id,bytes);jdbc.update("update feed_source set last_success_at=now(),error_code=null,item_count=(select count(*) from feed_item where source_id=?) where id=?",id,id);
    }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();jdbc.update("update feed_source set error_code='FEED_UNAVAILABLE' where id=?",id);}
   }
   return sources();
  }finally{busy.set(false);}
 }
 private static String text(Element e,String name){var n=e.getElementsByTagNameNS("*",name);return n.getLength()==0?"":n.item(0).getTextContent();}
 public static String normalize(String raw){
  URI u=URI.create(raw);if(u.getHost()==null||u.getUserInfo()!=null||!Set.of("http","https").contains(u.getScheme()))throw new IllegalArgumentException();
  try{return new URI(u.getScheme().toLowerCase(Locale.ROOT),null,u.getHost().toLowerCase(Locale.ROOT),u.getPort(),u.getPath(),u.getQuery()==null?null:Arrays.stream(u.getQuery().split("&")).filter(s->!s.toLowerCase(Locale.ROOT).startsWith("utm_")).sorted().reduce((a,b)->a+"&"+b).orElse(null),null).normalize().toASCIIString();}catch(URISyntaxException ex){throw new IllegalArgumentException();}
 }
 private void collectFeed(String source,byte[] xml)throws Exception{
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
  var builder=f.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());var document=builder.parse(new ByteArrayInputStream(xml));
  String root=document.getDocumentElement().getLocalName();if(root==null)root=document.getDocumentElement().getNodeName();NodeList entries;
  boolean atom="feed".equalsIgnoreCase(root);
  if(atom)entries=document.getElementsByTagNameNS("*","entry");
  else if("rss".equalsIgnoreCase(root))entries=document.getElementsByTagName("item");
  else throw new IOException();
  for(int i=0;i<Math.min(entries.getLength(),100);i++){
   var e=(Element)entries.item(i);String external=text(e,atom?"id":"guid"),title=text(e,"title"),url="";
   var links=e.getElementsByTagNameNS("*","link");for(int j=0;j<links.getLength();j++){var link=(Element)links.item(j);if(atom){if(link.getAttribute("rel").isEmpty()||link.getAttribute("rel").equals("alternate")){url=link.getAttribute("href");break;}}else if(!link.getTextContent().isBlank()){url=link.getTextContent().strip();break;}}
   try{url=normalize(url);}catch(IllegalArgumentException invalid){continue;}
   if(external.isBlank())external=url;if(external.length()>2000||title.isBlank()||url.length()>2000)continue;
   title=title.substring(0,Math.min(500,title.length()));String content=text(e,"content");if(content.isBlank())content=text(e,"summary");
   content=content.replaceAll("(?s)<[^>]+>"," ").replaceAll("\\s+"," ").strip();content=content.substring(0,Math.min(content.length(),20000));
   Instant published=null;try{String raw=atom?(text(e,"published").isBlank()?text(e,"updated"):text(e,"published")):text(e,"pubDate");published=atom?Instant.parse(raw):ZonedDateTime.parse(raw,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();}catch(Exception ignored){}
   saveItem(source,external,title,url,content,published);
  }
 }
 private void collectHtml(String source,String platform,String profileUrl,byte[] html)throws Exception{
  Document doc=Jsoup.parse(new ByteArrayInputStream(html),null,profileUrl);String itemSelector,titleSelector,summarySelector;
  switch(platform){
   case "jisilu"->{itemSelector=".aw-item";titleSelector=".aw-item-title a";summarySelector=".aw-item-content";}
   case "taoguba"->{itemSelector=".blog-list .blog-item";titleSelector="a";summarySelector=".blog-summary|p";}
   case "ths"->{itemSelector=".feed-item";titleSelector=".title|a";summarySelector=".feed-content|p";}
   case "jiuquaner"->{itemSelector=".post-item";titleSelector=".post-title|a";summarySelector=".post-summary|p";}
   case "youzhiyouxing"->{itemSelector=".topic-item";titleSelector=".topic-title|a";summarySelector=".topic-summary|p";}
   default->throw new IOException("Unsupported scraper platform");
  }
  for(org.jsoup.nodes.Element item:doc.select(itemSelector).stream().limit(100).toList()){
   org.jsoup.nodes.Element titleEl=first(item,titleSelector);if(titleEl==null)continue;String title=titleEl.text().strip(),rawUrl=titleEl.attr("abs:href");
   if(rawUrl.isBlank())continue;String url;try{url=normalize(rawUrl);}catch(IllegalArgumentException invalid){continue;}
   org.jsoup.nodes.Element summaryEl=first(item,summarySelector);String content=summaryEl==null?"":summaryEl.text().strip();
   if(title.isBlank())continue;saveItem(source,url,title,url,content,null);
  }
 }
 private static org.jsoup.nodes.Element first(org.jsoup.nodes.Element item,String selector){for(String candidate:selector.split("\\|")){org.jsoup.nodes.Element found=item.selectFirst(candidate.strip());if(found!=null)return found;}return null;}
 private void saveItem(String source,String external,String title,String url,String content,Instant published){
  String fp=json.hash(title+"\n"+url+"\n"+content);
  jdbc.update("insert into feed_item(id,source_id,external_id,url,title,content,published_at,fingerprint,created_at,updated_at) values(?,?,?,?,?,?,?,?,now(),now()) on conflict(url) do update set title=excluded.title,content=excluded.content,published_at=excluded.published_at,fingerprint=excluded.fingerprint,version=feed_item.version+1,updated_at=now() where feed_item.fingerprint<>excluded.fingerprint",UUID.randomUUID(),source,external,url,title,content,published==null?null:java.sql.Timestamp.from(published),fp);
 }
}
