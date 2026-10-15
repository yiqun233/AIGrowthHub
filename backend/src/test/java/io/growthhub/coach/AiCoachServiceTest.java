package io.growthhub.coach;
import io.growthhub.common.ApiException;
import io.growthhub.task.TaskRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiCoachServiceTest {
    private final AiSettingRepository settings=mock(AiSettingRepository.class);
    private final AiAnalysisRepository analyses=mock(AiAnalysisRepository.class);
    private final PlanSuggestionRepository suggestions=mock(PlanSuggestionRepository.class);
    private final AiConfiguration config=mock(AiConfiguration.class);
    private final AiContextService context=mock(AiContextService.class);
    private final AiJson json=mock(AiJson.class);
    private final AiCoachService service=new AiCoachService(settings,analyses,suggestions,mock(TaskRepository.class),config,context,mock(AiResultValidator.class),json,mock(JdbcTemplate.class));
    private AiContracts.Generate request(){return new AiContracts.Generate(UUID.randomUUID(),LocalDate.of(2038,1,1),60,AiContracts.Provider.OPENAI,"unit-model","");}
    @Test void dailyLimitRejectsBeforeReadingPrivateContext(){
        when(json.write(any())).thenReturn("input");when(json.hash("input")).thenReturn("hash");
        when(analyses.findByRequestKey("unit-key-0001")).thenReturn(Optional.empty());
        when(config.url(AiContracts.Provider.OPENAI)).thenReturn("https://api.openai.com/v1");when(config.key(AiContracts.Provider.OPENAI)).thenReturn("unit-placeholder");
        when(config.zone()).thenReturn(ZoneId.of("Asia/Shanghai"));when(config.limit()).thenReturn(2);
        when(analyses.countByCreatedAtGreaterThanEqual(any())).thenReturn(2L);
        ApiException error=assertThrows(ApiException.class,()->service.create("unit-key-0001",request()));
        assertEquals(429,error.status);assertEquals("DAILY_LIMIT",error.code);verifyNoInteractions(context);verify(analyses,never()).saveAndFlush(any());
    }
    @Test void changedInputCannotReuseAnExistingRequestKey(){
        when(json.write(any())).thenReturn("input");when(json.hash("input")).thenReturn("new-hash");
        var existing=new AiAnalysis();existing.requestHash="old-hash";
        when(analyses.findByRequestKey("unit-key-0002")).thenReturn(Optional.of(existing));
        var error=assertThrows(ApiException.class,()->service.create("unit-key-0002",request()));
        assertEquals("IDEMPOTENCY_CONFLICT",error.code);verifyNoInteractions(config,context);verify(analyses,never()).saveAndFlush(any());
    }
    @Test void missingKeyDoesNotCreateAJob(){
        when(json.write(any())).thenReturn("input");when(json.hash("input")).thenReturn("hash");
        when(analyses.findByRequestKey("unit-key-0003")).thenReturn(Optional.empty());when(config.url(AiContracts.Provider.OPENAI)).thenReturn("https://api.openai.com/v1");when(config.key(AiContracts.Provider.OPENAI)).thenReturn("");
        var error=assertThrows(ApiException.class,()->service.create("unit-key-0003",request()));
        assertEquals("PROVIDER_NOT_CONFIGURED",error.code);verifyNoInteractions(context);verify(analyses,never()).saveAndFlush(any());
    }
    @Test void abandonedRunningJobExpiresWithoutTouchingCurrentWork(){
        when(config.timeout()).thenReturn(90);
        var old=new AiAnalysis();old.status=AiAnalysis.Status.RUNNING;old.startedAt=Instant.now().minusSeconds(120);
        var current=new AiAnalysis();current.status=AiAnalysis.Status.RUNNING;current.startedAt=Instant.now();
        when(analyses.findByStatus(AiAnalysis.Status.RUNNING)).thenReturn(List.of(old,current));
        service.expireAbandoned();assertEquals(AiAnalysis.Status.FAILED,old.status);assertEquals("ANALYSIS_EXPIRED",old.errorCode);
        assertEquals(AiAnalysis.Status.RUNNING,current.status);assertNull(current.errorCode);
    }

}
