package com.driedfish.project_psychology_health_assist.AIService.memory;

// 记忆条目类型。刻意不复用 Spring AI 的 MessageType：它只有 USER/ASSISTANT/SYSTEM/TOOL，
// 没有"摘要"这一档；摘要既不是用户的话也不是 AI 的话，混进对话消息会让语义失真。
public enum MemoryMessageType {

    USER,
    AI,
    SUMMARY;

    // 从 Redis 里的 token 解析类型：兼容历史数据里的 ASSISTANT，未知 token 返回 null 由调用方跳过。
    public static MemoryMessageType fromToken(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.trim().toUpperCase()) {
            case "USER" -> USER;
            case "AI", "ASSISTANT" -> AI;
            case "SUMMARY" -> SUMMARY;
            default -> null;
        };
    }
}
