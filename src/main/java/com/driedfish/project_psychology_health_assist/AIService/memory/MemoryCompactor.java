package com.driedfish.project_psychology_health_assist.AIService.memory;

import com.driedfish.project_psychology_health_assist.AIService.PromptManage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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
@Component
public class MemoryCompactor {

    private final ChatClient memoryCompactClient;
    private final ConversationMemoryStore store;
    private final int threshold;
    private final int keep;

    public MemoryCompactor(@Qualifier("memoryCompact") ChatClient memoryCompactClient,
                           ConversationMemoryStore store,
                           @Value("${memory.compact.threshold:30}") int threshold,
                           @Value("${memory.compact.keep:10}") int keep) {
        this.memoryCompactClient = memoryCompactClient;
        this.store = store;
        this.threshold = threshold;
        this.keep = keep;
    }

    /**
     * 消息数达到 {@code threshold} 才压缩，压完只留最新的 {@code keep} 条。
     *
     * <p>测试时把阈值调到 5、keep 调到 2，一轮对话就能看到效果。</p>
     *
     * @return 是否真的执行了压缩
     */
    public boolean compactIfNeeded(String conversationId) {
        // TODO:
        //   1) if (store.size(conversationId) < threshold) return false;
        //   2) MemorySnapshot snapshot = store.snapshot(conversationId, Integer.MAX_VALUE);
        //   3) 待压缩 = 最旧的 (总数 - keep) 条；剩余 keep 条留在 Redis 里
        //   4) String newSummary = summarize(snapshot.summary(), toCompact);
        //      if (newSummary == null) return false;      ← 失败不删消息
        //   5) store.saveSummary(conversationId, newSummary);   ← 顺序不能反
        //   6) store.trimTo(conversationId, keep);
        //   7) 整个过程用 try-catch 吞异常，只记日志
        throw new UnsupportedOperationException("TODO: 实现 compactIfNeeded");
    }

    /**
     * 调用 memoryCompact 通道生成新摘要（纯模型调用，不写库）。
     *
     * @param oldSummary 旧摘要，可为 null
     * @param entries    待压缩的消息，按时间顺序
     * @return 新摘要；模型返回空则返回 null
     */
    public String summarize(String oldSummary, List<MemoryEntry> entries) {
        // TODO:
        //   String input = PromptManage.MEMORY_COMPACT_PROMPT
        //           + "\n旧摘要：\n" + (StringUtils.hasText(oldSummary) ? oldSummary : "无")
        //           + "\n新增对话：\n" + entries 逐行拼 "[用户] ..." / "[AI] ..."（内容截断 60 字）
        //   String content = memoryCompactClient.prompt().user(input).call().content();
        //   return StringUtils.hasText(content) ? content.trim() : null;
        throw new UnsupportedOperationException("TODO: 实现 summarize");
    }
}
