package io.growthhub.coach;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class AiContracts {
    private AiContracts() {}
    public enum Provider { OPENAI, DEEPSEEK, ANTHROPIC, GEMINI, QWEN, ZHIPU, MOONSHOT, DOUBAO, OPENROUTER, CUSTOM }
    public record Generate(@NotNull UUID sourceLogId,@NotNull LocalDate targetDate,
        @NotNull @Min(0) @Max(1440) Integer budgetMinutes,@NotNull Provider provider,
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}") String model,
        @Size(max=2000) String feedback) {}
    public record Evidence(UUID logId,String quote) {}
    public record ProposedTask(@NotNull UUID itemId,@NotBlank @Size(max=200) String title,
        @NotBlank @Size(max=2000) String reason,@NotNull @Min(1) @Max(1440) Integer estimatedMinutes,
        @NotBlank @Size(max=5000) String acceptanceCriteria,UUID goalId,UUID existingTaskId) {}
    public record Result(String summary,List<String> learnedTopics,List<Evidence> evidence,
        List<String> openQuestions,String masteryAssessment,List<ProposedTask> tomorrowTasks,String planAdjustmentReason) {}
    public record Edit(@NotNull Long version,@NotNull LocalDate targetDate,
        @NotNull @Size(max=3) List<@NotNull @Valid ProposedTask> tasks) {}
    public record Version(@NotNull Long version) {}
    public record Confirmed(UUID suggestionId,List<UUID> taskIds) {}
    public record ProviderView(Provider id,String label,boolean configured,String defaultModel) {}
    public record Settings(Long version,Provider provider,String model,int dailyMinutes,String preferences,
        String timezone,int dailyLimit,int timeoutSeconds,List<ProviderView> providers) {}
    public record SettingsInput(@NotNull Long version,@NotNull Provider provider,
        @NotNull @Pattern(regexp="|[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}") String model,
        @NotNull @Min(0) @Max(1440) Integer dailyMinutes,@NotNull @Size(max=2000) String preferences,@Size(max=100) String timezone) {}
}
