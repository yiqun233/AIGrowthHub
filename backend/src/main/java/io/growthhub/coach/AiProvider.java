package io.growthhub.coach;
public interface AiProvider {
    record Reply(String content,Long inputTokens,Long outputTokens) {}
    Reply generate(AiAnalysis analysis,long deadlineNanos,String correction);
}
