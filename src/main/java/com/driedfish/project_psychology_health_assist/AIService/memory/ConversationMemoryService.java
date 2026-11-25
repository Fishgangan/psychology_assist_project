package com.driedfish.project_psychology_health_assist.AIService.memory;

import com.driedfish.project_psychology_health_assist.AIService.PromptManage;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

//记忆门面：业务层唯一入口。
@Slf4j
@Service
public class ConversationMemoryService {

    private final ConversationMemoryStore store;
    private final ChatContextAssembler assembler;
    private final MemoryCompactor compactor;
    private final ConsultationSessionService sessionService;
    // 记忆窗口大小：最近 30 条消息
    private final int window;

    public ConversationMemoryService(ConversationMemoryStore store,
                                     ChatContextAssembler assembler,
                                     MemoryCompactor compactor,
                                     ConsultationSessionService sessionService,
                                     @Value("${memory.window:30}") int window) {
        this.store = store;
        this.assembler = assembler;
        this.compactor = compactor;
        this.sessionService = sessionService;
        this.window = window;
    }

    //准备本轮上下文：读快照（Redis 缺摘要时回落 MySQL）→ 装配 → 补当前用户消息 → 记录用户消息
    public List<Message> prepare(String conversationId, Long dbSessionId, String userMessage) {
        MemorySnapshot snapshot = store.snapshot(conversationId, window);
        // Redis 里的摘要过期/被清时，从 MySQL 真源回落，并顺手回填 Redis
        String summary = snapshot.summary();
        if (!StringUtils.hasText(summary)) {
            try {
                summary = sessionService.getMemorySummary(dbSessionId);
                if (StringUtils.hasText(summary)) {
                    store.saveSummary(conversationId, summary);
                }
            } catch (Exception e) {
                // 回落失败不影响对话，最多这一轮少一段摘要
                log.warn("回落读取 MySQL 摘要失败，sessionId：{}", dbSessionId, e);
            }
        }
        MemorySnapshot effective = new MemorySnapshot(summary, snapshot.entries());
        String baseSystemPrompt = PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT;
        List<Message> messages = assembler.assemble(effective, baseSystemPrompt, window);
        messages = assembler.appendCurrentUserMessage(messages, userMessage);
        try{
            store.append(conversationId, List.of(MemoryEntry.user(userMessage)));
        }catch(Exception e){
            log.warn("记录用户消息失败，对话 ID：{}，错误信息：{}", conversationId, e.getMessage());
        }
        return messages;

    }

    //记录本轮 AI 回复（流结束后调用）
    public void record(String conversationId, String assistantMessage) {
        try{
            store.append(conversationId, List.of(MemoryEntry.ai(assistantMessage)));
        }catch(Exception e){
            log.warn("记录 AI 回复失败，对话 ID：{}，错误信息：{}", conversationId, e.getMessage());
        }
    }

    //达到阈值就压缩（异步调用，不要阻塞流式响应）
    public void compactIfNeeded(String conversationId, Long dbSessionId) {
        compactor.compactIfNeeded(conversationId, dbSessionId);
    }
    public void clear(String conversationId) {
        store.clear(conversationId);
    }
}
