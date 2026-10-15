package io.growthhub.coach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AiWireCodecTest {
    private AiAnalysis analysis(AiContracts.Provider p){var a=new AiAnalysis();a.provider=p;a.model="test-model";a.inputSnapshot="{}";return a;}
    @Test void defaultsAndCustomConfiguration(){
        var c=new AiConfiguration(new MockEnvironment());assertEquals("",c.url(AiContracts.Provider.CUSTOM));
        assertEquals("TEXT",c.outputMode(AiContracts.Provider.CUSTOM));assertEquals("max_completion_tokens",c.tokenParameter(AiContracts.Provider.DOUBAO));
        for(var p:AiContracts.Provider.values())assertFalse(c.label(p).isBlank());
    }
    @Test void rejectsUnsafeAddressesAndUnknownModes(){
        for(String url:List.of("http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?key=secret","https://example.com/v1#fragment","")){
            var c=new AiConfiguration(new MockEnvironment().withProperty("CUSTOM_BASE_URL",url));
            assertThrows(ProviderFailure.class,()->AiWireCodec.request(c,analysis(AiContracts.Provider.CUSTOM),"system",Map.of()));
        }
        var c=new AiConfiguration(new MockEnvironment().withProperty("OPENAI_OUTPUT_MODE","TYPO"));
        assertThrows(ProviderFailure.class,()->AiWireCodec.request(c,analysis(AiContracts.Provider.OPENAI),"system",Map.of()));
    }
    @Test void customTextAndTokenOverride(){
        var c=new AiConfiguration(new MockEnvironment().withProperty("CUSTOM_BASE_URL","https://example.com/v1").withProperty("CUSTOM_TOKEN_PARAMETER","max_completion_tokens"));
        var req=AiWireCodec.request(c,analysis(AiContracts.Provider.CUSTOM),"system",Map.of());
        assertFalse(req.body().containsKey("response_format"));assertTrue(req.body().containsKey("max_completion_tokens"));
        assertEquals("https://example.com/v1/chat/completions",req.uri().toString());
    }
    @Test void claudeSchemaIsExplicitOptIn(){
        var c=new AiConfiguration(new MockEnvironment().withProperty("ANTHROPIC_OUTPUT_MODE","JSON_SCHEMA"));
        var req=AiWireCodec.request(c,analysis(AiContracts.Provider.ANTHROPIC),"system",Map.of("type","object"));
        assertTrue(req.body().containsKey("output_config"));assertFalse(req.headers().containsKey("Authorization"));
        assertEquals("2023-06-01",req.headers().get("anthropic-version"));
    }
    @Test void truncatedOrBlockedOutputNeverPasses(){
        var mapper=new ObjectMapper();
        assertThrows(ProviderFailure.class,()->AiWireCodec.reply(AiContracts.Provider.OPENAI,mapper.readTree("{\"choices\":[{\"finish_reason\":\"length\"}]}")));
        assertThrows(ProviderFailure.class,()->AiWireCodec.reply(AiContracts.Provider.ANTHROPIC,mapper.readTree("{\"stop_reason\":\"max_tokens\"}")));
        assertThrows(ProviderFailure.class,()->AiWireCodec.reply(AiContracts.Provider.GEMINI,mapper.readTree("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}")));
    }
    @Test void emptyTextCannotBecomeSuccessfulReply(){
        var mapper=new ObjectMapper();
        assertThrows(ProviderFailure.class,()->AiWireCodec.reply(AiContracts.Provider.ANTHROPIC,mapper.readTree("{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"hidden\"}]}")));
        assertThrows(ProviderFailure.class,()->AiWireCodec.reply(AiContracts.Provider.GEMINI,mapper.readTree("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"}]}}]}")));
    }
}
