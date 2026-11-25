package com.driedfish.project_psychology_health_assist.AIService.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

//记忆的持久化层：只和 Redis 打交道，不含任何业务判断。
@Slf4j
@Component
public class ConversationMemoryStore {

    private static final String MESSAGE_KEY_PREFIX = "chat_memory:";
    private static final String SUMMARY_KEY_PREFIX = "chat_summary:";
    private static final Duration MESSAGE_TTL = Duration.ofDays(7);
    private static final Duration SUMMARY_TTL = Duration.ofDays(30);
    private static final int MAX_MESSAGES = 30;

    private final StringRedisTemplate redisTemplate;

    public ConversationMemoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //读取一次对话的快照：摘要 + 最近 limit 条消息。

    public MemorySnapshot snapshot(String conversationId, int limit) {
        try{
        String summary = redisTemplate.opsForValue().get(SUMMARY_KEY_PREFIX + conversationId);
        List<MemoryEntry> entries = recent(conversationId, limit);
        return new MemorySnapshot(summary, entries);
        }catch (Exception e){
            log.warn("读取记忆快照失败", e);
            return MemorySnapshot.empty();
        }
    }
    // 读取最近 limit 条原始消息，按时间顺序（最旧 → 最新）。
    public List<MemoryEntry> recent(String conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> raw = redisTemplate.opsForList()
                .range(MESSAGE_KEY_PREFIX + conversationId, -limit, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<MemoryEntry> entries = new ArrayList<>(raw.size());
        for (String line : raw) {
            // 限 2 段：正文里带 '|' 也不会被截断
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            MemoryMessageType type = MemoryMessageType.fromToken(parts[0]);
            // 脏数据（未知类型）直接跳过，别让一条坏数据废掉整个会话记忆
            if (type == null || type == MemoryMessageType.SUMMARY) {
                continue;
            }
            entries.add(new MemoryEntry(type, parts[1]));
        }
        return entries;
    }

   // 追加消息：rightPushAll + LTRIM 保留最近 MAX_MESSAGES 条 + 刷新 TTL。
    public void append(String conversationId, List<MemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String key = MESSAGE_KEY_PREFIX + conversationId;
        List<String> values = entries.stream()
                .map(e -> e.type().name() + "|" + e.content())
                .toList();
        redisTemplate.opsForList().rightPushAll(key, values);
        redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
        redisTemplate.expire(key, MESSAGE_TTL);
    }

    //覆盖写入会话摘要（压缩后调用），重置摘要 TTL。

    public void saveSummary(String conversationId, String summary) {
        String key = SUMMARY_KEY_PREFIX + conversationId;
        if (!StringUtils.hasText(summary)) {
            redisTemplate.delete(key);
            return;
        }
        redisTemplate.opsForValue().set(key, summary, SUMMARY_TTL);
    }

    //只保留最近 keep 条原始消息（压缩后把已经进了摘要的旧消息裁掉）。

    public void trimTo(String conversationId, int keep) {
        if (keep < 0) {
            return;
        }
        redisTemplate.opsForList().trim(MESSAGE_KEY_PREFIX + conversationId, -keep, -1);
    }

    //当前原始消息条数（不含摘要），压缩阈值判定用。
    public long size(String conversationId) {
        Long size = redisTemplate.opsForList().size(MESSAGE_KEY_PREFIX + conversationId);
        return size == null ? 0L : size;
    }

    //清空会话记忆（会话删除时调用）：两个 key 一起删。

    public void clear(String conversationId) {
        redisTemplate.delete(List.of(
                MESSAGE_KEY_PREFIX + conversationId,
                SUMMARY_KEY_PREFIX + conversationId));
    }
}
