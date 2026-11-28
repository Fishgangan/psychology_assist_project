package com.driedfish.project_psychology_health_assist.AIService.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

// 上下文装配层：把 MemorySnapshot 翻译成 List<Message>
@Component
public class ChatContextAssembler {

    // 历史 AI 消息截断长度：回复常达 600+ tokens，不截会持续膨胀；500 字能保住中间要点
    private static final int MAX_HISTORY_AI_CHARS = 500;

    // 历史用户消息截断长度：可能粘贴长文本，但信息密度更高，额度与 AI 一致
    private static final int MAX_HISTORY_USER_CHARS = 300;

    // 摘要段落的标题，便于模型区分"背景记忆"和"角色设定"
    private static final String SUMMARY_HEADER = "【历史对话摘要】";

    // 装配本轮完整 messages（含唯一一条 system）；当前用户消息不在这里加
    public List<Message> assemble(MemorySnapshot snapshot, String baseSystemPrompt, int window) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(composeSystemPrompt(baseSystemPrompt, snapshot)));
        for (MemoryEntry entry : snapshot.entries()) {
            if (entry.type() == MemoryMessageType.SUMMARY) {
                continue;
            }
            if (entry.type() == MemoryMessageType.USER) {
                // 头部 + 尾部截断，保住用户最后说的重点
                messages.add(new UserMessage(
                        MemoryTextTruncator.headAndTail(entry.content(), MAX_HISTORY_USER_CHARS)));
            } else if (entry.type() == MemoryMessageType.AI) {
                // AI 的引导提问在结尾，头+尾截断，别只留开头
                messages.add(new AssistantMessage(
                        MemoryTextTruncator.headAndTail(entry.content(), MAX_HISTORY_AI_CHARS)));
            }
            // window 是历史消息数；下标 0 是 system，永不参与裁剪
            if (messages.size() > window + 1) {
                messages.remove(1);
            }
        }
        return messages;
    }

    // 组装系统提示：角色设定 +（有摘要时）摘要段落
    public String composeSystemPrompt(String baseSystemPrompt, MemorySnapshot snapshot) {
        String base = StringUtils.hasText(baseSystemPrompt) ? baseSystemPrompt : "";
        if (!snapshot.hasSummary()) {
            return base;
        }
        return base + "\n\n" + SUMMARY_HEADER + "\n" + snapshot.summary();
    }

    // 补上"当前这句用户消息"：拷贝一份再 add，不修改入参
    public List<Message> appendCurrentUserMessage(List<Message> messages, String userMessage) {
        List<Message> withCurrent = new ArrayList<>(messages);
        withCurrent.add(new UserMessage(userMessage));
        return withCurrent;
    }
}
