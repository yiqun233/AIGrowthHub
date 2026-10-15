package io.growthhub.coach;
import jakarta.persistence.*;
@Entity @Table(name="app_setting")
public class AiSetting {
    @Id public Integer id=1;
    @Version public Long version;
    @Enumerated(EnumType.STRING) public AiContracts.Provider defaultProvider=AiContracts.Provider.OPENAI;
    public String defaultModel="";
    public int dailyMinutes=60;
    public String preferences="";
    public String timezone="";
}
