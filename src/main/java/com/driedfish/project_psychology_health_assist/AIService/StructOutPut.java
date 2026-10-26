package com.driedfish.project_psychology_health_assist.AIService;

public class StructOutPut {
    public record StreamChatSession(String sessionId,
                                    Long userHash,
                                    String initialMessage,
                                    Long startTime,
                                    Long expiryTime,
                                    Integer messageCount,
                                    String status) {}
}
