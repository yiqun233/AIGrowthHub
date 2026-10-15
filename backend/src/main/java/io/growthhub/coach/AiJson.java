package io.growthhub.coach;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.core.type.TypeReference;
@Component
public class AiJson {
    public final ObjectMapper mapper;
    public AiJson(ObjectMapper mapper){this.mapper=mapper;}
    public String write(Object value){return mapper.writeValueAsString(value);}
    public <T> T read(String value,Class<T> type){return mapper.readValue(value,type);}
    public <T> T read(String value,TypeReference<T> type){return mapper.readValue(value,type);}
    public JsonNode tree(String value){return mapper.readTree(value);}
    public String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
