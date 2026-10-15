package io.growthhub.coach;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;
/** Provider wire protocols; application output validation remains shared. */
public final class AiWireCodec {
    private AiWireCodec(){}
    public record Request(URI uri,Map<String,String> headers,Map<String,Object> body){}
    public static Request request(AiConfiguration c,AiAnalysis a,String system,Object schema){
        var body=new LinkedHashMap<String,Object>();var headers=new LinkedHashMap<String,String>();
        String mode=c.outputMode(a.provider);
        if(!Set.of("TEXT","JSON_OBJECT","JSON_SCHEMA").contains(mode))throw configuration();
        String path;
        if(a.provider==AiContracts.Provider.ANTHROPIC){
            path="/messages";headers.put("x-api-key",c.key(a.provider));headers.put("anthropic-version","2023-06-01");
            body.put("model",a.model);body.put("max_tokens",4096);body.put("system",system);
            body.put("messages",List.of(Map.of("role","user","content",a.inputSnapshot)));
            if(mode.equals("JSON_SCHEMA"))body.put("output_config",Map.of("format",Map.of("type","json_schema","schema",schema)));
        }else if(a.provider==AiContracts.Provider.GEMINI){
            path="/models/"+URLEncoder.encode(a.model,StandardCharsets.UTF_8)+":generateContent";
            headers.put("x-goog-api-key",c.key(a.provider));
            body.put("systemInstruction",Map.of("parts",List.of(Map.of("text",system))));
            body.put("contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",a.inputSnapshot)))));
            var generation=new LinkedHashMap<String,Object>();generation.put("maxOutputTokens",4096);
            if(!mode.equals("TEXT"))generation.put("responseMimeType","application/json");
            // The application validates the full schema, including evidence and references.
            body.put("generationConfig",generation);
        }else{
            path="/chat/completions";headers.put("Authorization","Bearer "+c.key(a.provider));
            body.put("model",a.model);body.put("stream",false);
            body.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",a.inputSnapshot)));
            String tokens=c.tokenParameter(a.provider);
            if(!Set.of("max_tokens","max_completion_tokens").contains(tokens))throw configuration();
            body.put(tokens,4096);
            if(mode.equals("JSON_OBJECT"))body.put("response_format",Map.of("type","json_object"));
            if(mode.equals("JSON_SCHEMA"))body.put("response_format",Map.of("type","json_schema","json_schema",Map.of("name","growthhub_structured_v1","strict",true,"schema",schema)));
        }
        URI uri;
        try{uri=URI.create(c.url(a.provider).replaceAll("/+$","")+path);}catch(IllegalArgumentException e){throw configuration();}
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!("https".equals(uri.getScheme())||("http".equals(uri.getScheme())&&Set.of("127.0.0.1","localhost","[::1]","::1").contains(uri.getHost()))))throw configuration();
        return new Request(uri,headers,body);
    }
    private static ProviderFailure configuration(){return new ProviderFailure("INVALID_PROVIDER_CONFIG","提供商配置无效，请检查 HTTPS 基地址、输出模式和 token 参数",false,0);}
    private static Long tokens(JsonNode n,String key){return n.path(key).isIntegralNumber()?n.path(key).longValue():null;}
    public static AiProvider.Reply reply(AiContracts.Provider p,JsonNode n){
        String content;Long input,output;
        if(p==AiContracts.Provider.ANTHROPIC){
            if(!Set.of("end_turn","stop_sequence").contains(n.path("stop_reason").asString("")))throw incomplete();
            var text=new StringBuilder();for(var block:n.path("content"))if(block.path("type").asString("").equals("text")&&block.path("text").isString())text.append(block.path("text").stringValue());
            content=text.toString();input=tokens(n.path("usage"),"input_tokens");output=tokens(n.path("usage"),"output_tokens");
        }else if(p==AiContracts.Provider.GEMINI){
            var candidate=n.path("candidates").path(0);if(!candidate.path("finishReason").asString("").equals("STOP"))throw incomplete();
            var text=new StringBuilder();for(var part:candidate.path("content").path("parts"))if(!part.path("thought").asBoolean(false)&&part.path("text").isString())text.append(part.path("text").stringValue());
            content=text.toString();input=tokens(n.path("usageMetadata"),"promptTokenCount");output=tokens(n.path("usageMetadata"),"candidatesTokenCount");
        }else{
            var choice=n.path("choices").path(0);if(!choice.path("finish_reason").asString("").equals("stop"))throw incomplete();
            var value=choice.path("message").path("content");content=value.isString()?value.stringValue():"";
            input=tokens(n.path("usage"),"prompt_tokens");output=tokens(n.path("usage"),"completion_tokens");
        }
        if(content.isBlank())throw new ProviderFailure("INVALID_PROVIDER_RESPONSE","提供商未返回文本 JSON",true,0);
        return new AiProvider.Reply(content,input,output);
    }
    private static ProviderFailure incomplete(){return new ProviderFailure("INCOMPLETE_AI_OUTPUT","模型输出被截断、拦截或未正常结束",true,0);}
}
