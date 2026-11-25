package com.driedfish.project_psychology_health_assist.AIService.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文装配层：把 {@link MemorySnapshot} 翻译成 Spring AI 的 {@code List<Message>}。
 *
 * <p>纯函数，不注入任何 Bean，也不碰 Redis —— 可以直接写单测，
 * 不需要起 Spring 上下文、不需要连数据库。</p>
 *
 * <p><b>摘要注入方式（方案 b）：拼进系统提示文本，而不是单独一条 SystemMessage。</b></p>
 * <ul>
 *   <li>好处：整个 prompt 永远只有<b>一条</b> system 消息，不会出现"两条 system 谁生效"的问题；</li>
 *   <li>代价：系统提示词不再由 {@code ChatClientConfig} 的 {@code defaultSystem} 提供，
 *       而是从这里（装配层）统一产出 —— 也就是说 system 只有一个来源；</li>
 *   <li>为什么值：M6 要把"画像 + 情绪 + 摘要"都拼进系统提示，本来就需要一个统一的组装点，
 *       这里就是那个点。</li>
 * </ul>
 */
@Component
public class ChatContextAssembler {

    /** 历史消息窗口：超过就从最旧的开始裁；system 消息不参与裁剪。 */
    private static final int MAX_HISTORY_MESSAGES = 30;

    /** 摘要段落的标题，便于模型区分"背景记忆"和"角色设定"。 */
    private static final String SUMMARY_HEADER = "【历史对话摘要】";

    /**
     * 装配本轮完整的 messages（含唯一一条 system）。
     *
     * @param snapshot         记忆快照（摘要 + 历史消息）
     * @param baseSystemPrompt 角色提示词，通常传 {@code PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT}
     * @return 第一个元素一定是 SystemMessage；其后是历史消息；当前用户消息<b>不在这里加</b>
     */
    public List<Message> assemble(MemorySnapshot snapshot, String baseSystemPrompt) {
        // TODO:
        //   1) List<Message> messages = new ArrayList<>();
        //   2) messages.add(new SystemMessage(composeSystemPrompt(baseSystemPrompt, snapshot)));
        //   3) snapshot.entries() 逐条映射：
        //        USER → new UserMessage(content)
        //        AI   → new AssistantMessage(content)
        //        SUMMARY → 跳过（摘要已经在 system 里了，再放一遍就重复）
        //   4) 历史部分超过 MAX_HISTORY_MESSAGES 时，只保留最新的 N 条（注意别动下标 0 的 system）
        throw new UnsupportedOperationException("TODO: 实现 assemble");
    }

    /**
     * 组装系统提示：角色设定 + （有摘要时）摘要段落。
     *
     * <p>约定：没有摘要时，返回值必须和 {@code baseSystemPrompt} 完全一致
     * （不多一个换行、不多一个空段落），这样"没记忆"和"有记忆"的行为差异只有摘要本身。</p>
     */
    public String composeSystemPrompt(String baseSystemPrompt, MemorySnapshot snapshot) {
        // TODO:
        //   String base = StringUtils.hasText(baseSystemPrompt) ? baseSystemPrompt : "";
        //   if (!snapshot.hasSummary()) return base;
        //   return base + "\n\n" + SUMMARY_HEADER + "\n" + snapshot.summary();
        throw new UnsupportedOperationException("TODO: 实现 composeSystemPrompt");
    }

    /**
     * 给装配结果补上"当前这句用户消息"。
     *
     * <p>单独一个方法而不是塞进 {@link #assemble}，是为了让"当前消息只出现一次"
     * 这件事只有一个入口，排查重复注入时只看这里。</p>
     */
    public List<Message> appendCurrentUserMessage(List<Message> messages, String userMessage) {
        // TODO: 拷贝一份（new ArrayList<>(messages)）再 add(new UserMessage(userMessage))
        //   —— 不要就地修改入参，避免调用方复用同一个 list 时串味
        throw new UnsupportedOperationException("TODO: 实现 appendCurrentUserMessage");
    }
}
