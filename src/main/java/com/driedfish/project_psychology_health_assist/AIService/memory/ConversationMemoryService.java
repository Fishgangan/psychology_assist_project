package com.driedfish.project_psychology_health_assist.AIService.memory;

import com.driedfish.project_psychology_health_assist.AIService.PromptManage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆门面：业务层唯一入口。
 *
 * <p>调用方（{@code PsychologicalSupportService}）只需要三句话：</p>
 * <pre>
 *   List&lt;Message&gt; messages = memoryService.prepare(conversationId, userMessage);
 *   // ... 调模型，拿到完整回复 aiResponse ...
 *   memoryService.record(conversationId, aiResponse);
 *   memoryService.compactIfNeeded(conversationId);
 * </pre>
 *
 * <p>它不知道 Redis、不知道摘要、不知道裁剪 —— 这正是封装的标志。
 * 将来加用户画像、加情绪状态进上下文（M4 / M6），改动都收敛在
 * {@link ChatContextAssembler} 和这个类的 {@code prepare} 里。</p>
 */
@Service
public class ConversationMemoryService {

    private final ConversationMemoryStore store;
    private final ChatContextAssembler assembler;
    private final MemoryCompactor compactor;
    private final int window;

    public ConversationMemoryService(ConversationMemoryStore store,
                                     ChatContextAssembler assembler,
                                     MemoryCompactor compactor,
                                     @Value("${memory.window:30}") int window) {
        this.store = store;
        this.assembler = assembler;
        this.compactor = compactor;
        this.window = window;
    }

    /**
     * 准备本轮上下文：读快照 → 装配（含唯一 system）→ 补当前用户消息 → <b>落库用户消息</b>。
     *
     * <p>返回值已经是完整可用的 messages 列表：第一条是系统提示（角色设定 + 历史摘要），
     * 最后一条是当前这句用户消息。调用方直接 {@code .messages(...)} 即可，
     * <b>不要再调 {@code .system(...)}，也不要再调 {@code .user(...)}</b>，否则就是重复注入。</p>
     *
     * <p>为什么在调模型之前就写用户消息：和官方 {@code MessageChatMemoryAdvisor} 的行为一致，
     * 保证模型调用失败或客户端断连时，用户说过的话也不会丢。</p>
     */
    public List<Message> prepare(String conversationId, String userMessage) {
        // TODO:
        //   1) MemorySnapshot snapshot = store.snapshot(conversationId, window);
        //   2) List<Message> messages = assembler.assemble(snapshot, PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT);
        //   3) messages = assembler.appendCurrentUserMessage(messages, userMessage);
        //   4) store.append(conversationId, List.of(MemoryEntry.user(userMessage)));   // 失败只记日志
        //   5) return messages;
        throw new UnsupportedOperationException("TODO: 实现 prepare");
    }

    /**
     * 记录本轮 AI 回复（流结束后调用）。
     */
    public void record(String conversationId, String assistantMessage) {
        // TODO: store.append(conversationId, List.of(MemoryEntry.ai(assistantMessage)))
        // 失败只记日志：AI 回复已经吐给用户了，不能因为记忆失败而报错
        throw new UnsupportedOperationException("TODO: 实现 record");
    }

    /**
     * 达到阈值就压缩（异步调用，不要阻塞流式响应）。
     */
    public void compactIfNeeded(String conversationId) {
        // TODO: 交给 compactor，并吞掉异常 —— 压缩是增强能力，失败不能影响主链路
        throw new UnsupportedOperationException("TODO: 实现 compactIfNeeded");
    }
}
