package com.driedfish.project_psychology_health_assist.AIService.memory;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 一次对话的完整记忆快照：历史摘要 + 最近的原始消息。
 *
 * <p>摘要和历史分开存放（Redis 里是两个 key），所以这里也分成两个字段，
 * 让装配层有足够信息决定"摘要放哪、历史放哪"。</p>
 *
 * @param summary 历史摘要，可能为 null 或空串
 * @param entries 原始消息，按时间顺序（最旧 → 最新）
 */
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
