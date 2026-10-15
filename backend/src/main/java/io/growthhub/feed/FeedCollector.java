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
@Component
public class FeedCollector {
 private final JdbcTemplate jdbc;private final AiJson json;private final Environment env;
 private final AtomicBoolean busy=new AtomicBoolean();private Instant last=Instant.EPOCH;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public FeedCollector(JdbcTemplate jdbc,AiJson json,Environment env){this.jdbc=jdbc;this.json=json;this.env=env;}
 @Scheduled(fixedDelayString="${FEED_REFRESH_MS:900000}") public void scheduled(){if(env.getProperty("FEED_AUTO_REFRESH",Boolean.class,false))try{refresh();}catch(ApiException ignored){}}
 public List<Map<String,Object>> sources(){return jdbc.queryForList("select id,label,url,last_attempt_at,last_success_at,error_code,item_count from feed_source order by id");}
 public List<Map<String,Object>> refresh(){
  if(!busy.compareAndSet(false,true))throw new ApiException(409,"FEED_BUSY","资讯采集正在进行");
  try{
   if(Instant.now().isBefore(last.plusSeconds(10)))throw new ApiException(429,"FEED_COOLDOWN","请稍后再刷新资讯");last=Instant.now();
   for(var row:sources()){
    String id=(String)row.get("id");String url=env.getProperty("FEED_"+id.toUpperCase(Locale.ROOT).replace('-','_')+"_URL",(String)row.get("url"));
    jdbc.update("update feed_source set last_attempt_at=now() where id=?",id);
    try{
     URI uri=URI.create(url);if(uri.getUserInfo()!=null||uri.getHost()==null||!("https".equals(uri.getScheme())||"http".equals(uri.getScheme())&&Set.of("127.0.0.1","localhost").contains(uri.getHost())))throw new IOException();
     var future=http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("User-Agent","GrowthHub/0.1 feed reader").GET().build(),info->new io.growthhub.common.BoundedBody(2_000_000));
     HttpResponse<byte[]> res;try{res=future.get(12,java.util.concurrent.TimeUnit.SECONDS);}finally{if(!future.isDone())future.cancel(true);}
     byte[] bytes=res.body();if(res.statusCode()!=200)throw new IOException();
     collect(id,bytes);jdbc.update("update feed_source set last_success_at=now(),error_code=null,item_count=(select count(*) from feed_item where source_id=?) where id=?",id,id);
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
 private void collect(String source,byte[] xml)throws Exception{
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
  var builder=f.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());var document=builder.parse(new ByteArrayInputStream(xml));
  if(!"feed".equals(document.getDocumentElement().getLocalName()))throw new IOException();
  var entries=document.getElementsByTagNameNS("*","entry");
  for(int i=0;i<Math.min(entries.getLength(),100);i++){
   var e=(Element)entries.item(i);String external=text(e,"id"),title=text(e,"title"),url="";
   var links=e.getElementsByTagNameNS("*","link");for(int j=0;j<links.getLength();j++){var link=(Element)links.item(j);if(link.getAttribute("rel").isEmpty()||link.getAttribute("rel").equals("alternate")){url=link.getAttribute("href");break;}}
   try{url=normalize(url);}catch(IllegalArgumentException invalid){continue;}
   if(external.isBlank()||external.length()>2000||title.isBlank()||url.length()>2000)continue;
   title=title.substring(0,Math.min(500,title.length()));String content=text(e,"content");if(content.isBlank())content=text(e,"summary");
   content=content.replaceAll("(?s)<[^>]+>"," ").replaceAll("\\s+"," ").strip();content=content.substring(0,Math.min(content.length(),20000));
   Instant published=null;try{published=Instant.parse(text(e,"published").isBlank()?text(e,"updated"):text(e,"published"));}catch(Exception ignored){}
   String fp=json.hash(title+"\n"+url+"\n"+content);
   jdbc.update("insert into feed_item(id,source_id,external_id,url,title,content,published_at,fingerprint,created_at,updated_at) values(?,?,?,?,?,?,?,?,now(),now()) on conflict(url) do update set title=excluded.title,content=excluded.content,published_at=excluded.published_at,fingerprint=excluded.fingerprint,version=feed_item.version+1,updated_at=now() where feed_item.fingerprint<>excluded.fingerprint",UUID.randomUUID(),source,external,url,title,content,published==null?null:java.sql.Timestamp.from(published),fp);
  }
 }
}
