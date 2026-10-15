package io.growthhub.coach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.ZoneId;
@Configuration @EnableScheduling
public class AiConfiguration {
    private final Environment env;
    public AiConfiguration(Environment env){this.env=env;}
    @Value("${AI_TIMEOUT_SECONDS:90}") private int timeout;
    @Value("${AI_DAILY_LIMIT:20}") private int limit;
    @Value("${app.timezone}") private String timezone;
    private String value(AiContracts.Provider p,String suffix,String fallback){return env.getProperty(p.name()+suffix,fallback).strip();}
    public String key(AiContracts.Provider p){return value(p,"_API_KEY","");}
    public String model(AiContracts.Provider p){return value(p,"_MODEL","");}
    public String label(AiContracts.Provider p){return switch(p){
        case OPENAI->"OpenAI";case DEEPSEEK->"DeepSeek";case ANTHROPIC->"Claude / Anthropic";
        case GEMINI->"Google Gemini";case QWEN->"通义千问 / 百炼";case ZHIPU->"智谱 GLM";
        case MOONSHOT->"Kimi / Moonshot";case DOUBAO->"豆包 / 火山方舟";
        case OPENROUTER->"OpenRouter";case CUSTOM->"自定义 OpenAI 兼容接口";};}
    public String url(AiContracts.Provider p){return value(p,"_BASE_URL",switch(p){
        case OPENAI->"https://api.openai.com/v1";case DEEPSEEK->"https://api.deepseek.com";
        case ANTHROPIC->"https://api.anthropic.com/v1";case GEMINI->"https://generativelanguage.googleapis.com/v1beta";
        case QWEN->"https://dashscope.aliyuncs.com/compatible-mode/v1";
        case ZHIPU->"https://open.bigmodel.cn/api/paas/v4";case MOONSHOT->"https://api.moonshot.cn/v1";
        case DOUBAO->"https://ark.cn-beijing.volces.com/api/v3";case OPENROUTER->"https://openrouter.ai/api/v1";case CUSTOM->"";});}
    public String outputMode(AiContracts.Provider p){return value(p,"_OUTPUT_MODE",p==AiContracts.Provider.OPENAI?"JSON_SCHEMA":p==AiContracts.Provider.ANTHROPIC||p==AiContracts.Provider.CUSTOM?"TEXT":"JSON_OBJECT");}
    public String tokenParameter(AiContracts.Provider p){return value(p,"_TOKEN_PARAMETER",p==AiContracts.Provider.OPENAI||p==AiContracts.Provider.DOUBAO?"max_completion_tokens":"max_tokens");}
    public int timeout(){return Math.clamp(timeout,1,180);}
    public int limit(){return Math.clamp(limit,1,10000);}
    private io.growthhub.common.BusinessClock clock;
    @org.springframework.beans.factory.annotation.Autowired public void clock(io.growthhub.common.BusinessClock clock){this.clock=clock;}
    public ZoneId zone(){return clock==null?ZoneId.of(timezone):clock.zone();}
}
