package com.driedfish.project_psychology_health_assist.AIService.memory;

import com.driedfish.project_psychology_health_assist.AIService.PromptManage;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆压缩：把"旧摘要 + 最旧的一段对话"合并成新摘要，并裁掉已进入摘要的消息。
 *
 * <p>单独成类的原因：压缩是低频、重 IO、可失败的操作，
 * 和每轮都要跑的门面（{@link ConversationMemoryService}）生命周期完全不同。</p>
 *
 * <p>四条铁律：</p>
 * <ol>
 *   <li><b>增量压缩</b>：必须带上旧摘要，只压新消息会丢早期信息；</li>
 *   <li><b>失败不删原始消息</b>：摘要生成失败就直接返回，消息原样保留；</li>
 *   <li><b>先写摘要、再裁消息</b>：反过来一旦失败，被裁掉的对话就永久丢了；</li>
 *   <li><b>防重入</b>：用户连发两条时，两个请求可能同时压缩。</li>
 * </ol>
 */
@Slf4j
@Component
public class MemoryCompactor {

    private final ChatClient memoryCompactClient;
    private final ConversationMemoryStore store;
    private final ConsultationSessionService sessionService;
    // 压缩阈值：超过就压缩
    private final int threshold;
    // 保留最新消息数：超过就裁掉
    private final int keep;

    public MemoryCompactor(@Qualifier("memoryCompact") ChatClient memoryCompactClient,
                           ConversationMemoryStore store,
                           ConsultationSessionService sessionService,
                           @Value("${memory.compact.threshold:30}") int threshold,
                           @Value("${memory.compact.keep:10}") int keep) {
        this.memoryCompactClient = memoryCompactClient;
        this.store = store;
        this.sessionService = sessionService;
        this.threshold = threshold;
        this.keep = keep;
    }

    //消息数达到 {@code threshold} 才压缩，压完只留最新的 {@code keep} 条。
    // 测试时把阈值调到 5、keep 调到 2，一轮对话就能看到效果。
    public boolean compactIfNeeded(String conversationId, Long dbSessionId) {
        try {
            if(store.size(conversationId) < threshold) return false;
            MemorySnapshot snapshot=store.snapshot(conversationId, Integer.MAX_VALUE);
            int compactIndex=snapshot.entries().size()-keep;
            if(compactIndex < 0) return false;
            List<MemoryEntry> toCompact=snapshot.entries().subList(0, compactIndex);
            String newSummary=summarize(snapshot.summary(), toCompact);
            // 失败不删原始消息
            if(newSummary==null) return false;
            log.info("压缩记忆：{}，新摘要：{}", conversationId, newSummary);
            // ① 先写 MySQL（真源）：失败就抛出，不往下走，消息原样保留
            sessionService.updateMemorySummary(dbSessionId, newSummary);
            // ② 再写 Redis（快路径）：失败最多让下一轮走一次 MySQL 回落
            store.saveSummary(conversationId, newSummary);
            // ③ 最后裁消息：此时摘要已经至少落在一处，裁掉不会丢信息
            store.trimTo(conversationId, keep);
            return true;
        } catch (Exception e) {
            log.warn("压缩记忆失败：{}", conversationId, e);
            return false;
        }

    }

    //调用 memoryCompact 通道生成新摘要（纯模型调用，不写库）。
    public String summarize(String oldSummary, List<MemoryEntry> entries) {
        String prompt=PromptManage.MEMORY_COMPACT_PROMPT
                + "\n旧摘要：\n" + (StringUtils.hasText(oldSummary) ? oldSummary : "无")
                + "\n新增对话：\n" + entries.stream().map(entry -> entry.type() + " " +StringUtils.truncate((entry.content()),60)).collect(Collectors.joining("\n"));
        String content=memoryCompactClient.prompt().user(prompt).call().content();
        return StringUtils.hasText(content) ? content.trim() : null;
    }
}
