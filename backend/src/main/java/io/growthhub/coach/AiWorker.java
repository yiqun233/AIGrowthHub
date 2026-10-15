package io.growthhub.coach;
import io.growthhub.common.ApiException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
@Component
public class AiWorker {
    private final AiCoachService service;private final AiProvider provider;private final AiResultValidator validator;private final AiJson json;private final AiConfiguration config;
    private volatile boolean ready;
    public AiWorker(AiCoachService service,AiProvider provider,AiResultValidator validator,AiJson json,AiConfiguration config){this.service=service;this.provider=provider;this.validator=validator;this.json=json;this.config=config;}
    @EventListener(ApplicationReadyEvent.class) public void ready(){service.recover();ready=true;}
    @Scheduled(fixedDelay=750) public void tick(){
        if(!ready)return;
        service.expireAbandoned();
        var next=service.claim();if(next.isEmpty())return;var a=next.get();long start=System.nanoTime(),deadline=start+config.timeout()*1_000_000_000L;
        Long input=null,output=null;String correction="";String code="INVALID_AI_OUTPUT",message="模型输出不符合约定，可重试";
        for(int attempt=1;attempt<=2;attempt++){
            service.attempt(a.id,attempt);
            try{
                var reply=provider.generate(a,deadline,correction);
                input=reply.inputTokens()==null?null:(input==null?reply.inputTokens():input+reply.inputTokens());
                output=reply.outputTokens()==null?null:(output==null?reply.outputTokens():output+reply.outputTokens());
                var result=validator.parse(reply.content(),json.read(a.inputSnapshot,AiContextService.Snapshot.class));
                service.succeed(a.id,result,(System.nanoTime()-start)/1_000_000,input,output);return;
            }catch(ProviderFailure e){
                code=e.code;message=e.getMessage();if(!e.retryable||attempt==2)break;
                long delay=e.retryAfterSeconds*1_000_000_000L;
                if(delay>=deadline-System.nanoTime())break;
                if(delay>0)try{Thread.sleep(e.retryAfterSeconds*1000L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
            }catch(ApiException e){code=e.code;message=e.getMessage();correction=e.getMessage();if(attempt==2)break;}
            catch(RuntimeException e){
                code="ANALYSIS_INTERNAL_ERROR";message="分析暂时失败，原始记录已保留，可以重试";
                LoggerFactory.getLogger(AiWorker.class).warn("AI analysis {} failed: {}",a.id,e.getClass().getSimpleName());break;
            }
            if(System.nanoTime()>=deadline){code="PROVIDER_TIMEOUT";message="AI 分析超时，原始记录已保留，可以重试";break;}
        }
        service.fail(a.id,code,message,(System.nanoTime()-start)/1_000_000,input,output);
    }
}
