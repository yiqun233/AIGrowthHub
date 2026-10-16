package io.growthhub.export;
import io.growthhub.coach.AiJson;
import io.growthhub.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController
public class DataExportController {
    private final JdbcTemplate jdbc;private final AiJson json;
    public DataExportController(JdbcTemplate jdbc,AiJson json){this.jdbc=jdbc;this.json=json;}
    // Fixed table names only; a repeatable-read snapshot preserves all exported relations.
    @GetMapping("/api/v1/exports")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> export(){
        var data=new LinkedHashMap<String,Object>();var counts=new LinkedHashMap<String,Long>();long total=0;
        for(String table:List.of("learning_goal","learning_stage","learning_stage_review","learning_task","study_log","study_log_task","app_setting","ai_analysis","plan_suggestion","suggestion_task","weekly_plan","weekly_review","achievement","feed_source","feed_item","feed_candidate","ai_work")){
            long count=jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);total+=count;
            if(total>20000)throw new ApiException(413,"EXPORT_TOO_LARGE","数据超过在线导出上限，请使用数据库备份脚本");
            counts.put(table,count);
        }
        long estimatedBytes=0;
        for(String table:counts.keySet()){
            Long size=jdbc.queryForObject("SELECT coalesce(sum(octet_length(to_jsonb(t)::text)),0) FROM "+table+" t",Long.class);
            estimatedBytes+=size;
            if(estimatedBytes>50*1024*1024-65536)throw new ApiException(413,"EXPORT_TOO_LARGE","导出文件超过在线大小上限，请使用数据库备份脚本");
        }
        for(String table:counts.keySet()){
            String projection=switch(table){case "ai_analysis"->"to_jsonb(t)-'request_key'-'request_hash'-'context_hash'";case "ai_work"->"to_jsonb(t)-'request_key'-'request_hash'-'source_hash'";case "weekly_review","learning_stage_review"->"to_jsonb(t)-'source_hash'";default->"to_jsonb(t)";};
            data.put(table,jdbc.query("SELECT ("+projection+")::text FROM "+table+" t ORDER BY (to_jsonb(t))::text",(rs,row)->json.read(rs.getString(1),Object.class)));
        }
        var result=new LinkedHashMap<String,Object>();result.put("schemaVersion","growthhub-export-v4");result.put("exportedAt",Instant.now().toString());result.put("counts",counts);result.put("data",data);
        byte[] bytes=json.write(result).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>50*1024*1024)throw new ApiException(413,"EXPORT_TOO_LARGE","导出文件超过 50 MB，请使用数据库备份脚本");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"GrowthHub-export.json\"")
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
