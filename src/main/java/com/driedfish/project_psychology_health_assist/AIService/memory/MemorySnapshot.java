package com.driedfish.project_psychology_health_assist.AIService.memory;

import org.springframework.util.StringUtils;

import java.util.List;

// 一次对话的记忆快照：历史摘要 + 最近消息。
// 分成两个字段，因为它们在 Redis 里就是两个 key：摘要可独立过期与覆盖。
public record MemorySnapshot(String summary, List<MemoryEntry> entries) {

    public MemorySnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static MemorySnapshot empty() {
        return new MemorySnapshot(null, List.of());
    }

    public boolean hasSummary() {
        return StringUtils.hasText(summary);
    }
}
