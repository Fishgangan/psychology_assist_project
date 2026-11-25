package com.driedfish.project_psychology_health_assist.AIService.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

// 上下文装配层：把 MemorySnapshot 翻译成 Spring AI 的 {@code List<Message>}
@Component
public class ChatContextAssembler {


    /** 摘要段落的标题，便于模型区分"背景记忆"和"角色设定"。 */
    private static final String SUMMARY_HEADER = "【历史对话摘要】";

    /**
     * 装配本轮完整的 messages（含唯一一条 system）。
     *
     * @param snapshot         记忆快照（摘要 + 历史消息）
     * @param baseSystemPrompt 角色提示词，通常传 {@code PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT}
     * @return 第一个元素一定是 SystemMessage；其后是历史消息；当前用户消息<b>不在这里加</b>
     */
    public List<Message> assemble(MemorySnapshot snapshot, String baseSystemPrompt, int window) {
        List<Message> messages=new ArrayList<>();
        messages.add(new SystemMessage(composeSystemPrompt(baseSystemPrompt, snapshot)));
        for(MemoryEntry entry: snapshot.entries()){
            if(entry.type() == MemoryMessageType.SUMMARY) continue;
            if(entry.type() == MemoryMessageType.USER){
                messages.add(new UserMessage(entry.content()));
            }else if(entry.type() == MemoryMessageType.AI){
                messages.add(new AssistantMessage(entry.content()));
            }
            //window是历史消息数
            if(messages.size() > window+1){
                // 从最旧的开始裁剪，system 消息不参与裁剪。
                messages.remove(1);
            }
        }
        return messages;
    }

    //组装系统提示：角色设定 + （有摘要时）摘要段落。
    public String composeSystemPrompt(String baseSystemPrompt, MemorySnapshot snapshot) {
        String base=StringUtils.hasText(baseSystemPrompt)?baseSystemPrompt : "";
        if (!snapshot.hasSummary()) return base;
        return base + "\n\n" + SUMMARY_HEADER + "\n" + snapshot.summary();

    }

    //给装配结果补上"当前这句用户消息"。
    public List<Message> appendCurrentUserMessage(List<Message> messages, String userMessage) {
        List<Message> lastMessages=new ArrayList<>(messages);
        lastMessages.add(new UserMessage(userMessage));
        return lastMessages;
    }
}
