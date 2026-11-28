package com.driedfish.project_psychology_health_assist.AIService.memory;

import java.util.Objects;

// 一条记忆条目：类型 + 正文。跨层传递的合同，不依赖 Spring AI 的任何类型。
public record MemoryEntry(MemoryMessageType type, String content) {

    public MemoryEntry {
        Objects.requireNonNull(type, "type 不能为空");
        content = content == null ? "" : content;
    }

    public static MemoryEntry user(String content) {
        return new MemoryEntry(MemoryMessageType.USER, content);
    }

    public static MemoryEntry ai(String content) {
        return new MemoryEntry(MemoryMessageType.AI, content);
    }

    public static MemoryEntry summary(String content) {
        return new MemoryEntry(MemoryMessageType.SUMMARY, content);
    }
}
