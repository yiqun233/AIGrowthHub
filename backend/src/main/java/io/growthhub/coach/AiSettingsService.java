package io.growthhub.coach;
import io.growthhub.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class AiSettingsService {
    private final AiSettingRepository repo;private final AiConfiguration config;
    public AiSettingsService(AiSettingRepository repo,AiConfiguration config){this.repo=repo;this.config=config;}
    private AiContracts.Settings view(AiSetting s){
        var providers=Arrays.stream(AiContracts.Provider.values()).map(p->new AiContracts.ProviderView(p,config.label(p),!config.key(p).isBlank()&&!config.url(p).isBlank(),config.model(p))).toList();
        return new AiContracts.Settings(s.version,s.defaultProvider,s.defaultModel.isBlank()?config.model(s.defaultProvider):s.defaultModel,
            s.dailyMinutes,s.preferences,config.zone().toString(),config.limit(),config.timeout(),providers);
    }
    @Transactional(readOnly=true) public AiContracts.Settings get(){return view(repo.findById(1).orElseThrow());}
    @Transactional public AiContracts.Settings update(AiContracts.SettingsInput i){
        var s=repo.mutex();ApiException.version(s.version,i.version());s.defaultProvider=i.provider();s.defaultModel=i.model();
        if(i.timezone()!=null){try{if(!java.time.ZoneId.getAvailableZoneIds().contains(i.timezone())||i.timezone().startsWith("SystemV/"))throw new java.time.DateTimeException("IANA timezone required");java.time.ZoneId.of(i.timezone());}catch(java.time.DateTimeException e){throw new ApiException(400,"INVALID_TIMEZONE","请输入有效 IANA 时区，如 Asia/Shanghai 或 UTC");}s.timezone=i.timezone();}
        s.dailyMinutes=i.dailyMinutes();s.preferences=i.preferences();return view(repo.saveAndFlush(s));
    }
}
