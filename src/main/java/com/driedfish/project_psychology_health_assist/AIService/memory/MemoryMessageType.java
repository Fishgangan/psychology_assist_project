package com.driedfish.project_psychology_health_assist.AIService.memory;

/**
 * 记忆条目类型。
 *
 * <p>刻意不复用 Spring AI 的 {@code MessageType}：它只有 USER / ASSISTANT / SYSTEM / TOOL，
 * 没有"摘要"这一档。而摘要既不是用户的话，也不是 AI 的话，混进对话消息里会让上下文语义失真。</p>
 */
public enum MemoryMessageType {

    USER,
    AI,
    SUMMARY;

    /**
     * 从 Redis 里存储的 token 解析类型。
     *
     * <p>历史数据中 AI 回复记为 {@code ASSISTANT}，这里做兼容映射。
     * 无法识别的 token 返回 {@code null}，由调用方跳过 —— 脏数据不该炸掉整个会话记忆。</p>
     */
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
