package com.driedfish.project_psychology_health_assist.AIService.memory;

import java.util.regex.Pattern;

// 历史消息截断：保留头 + 尾，丢掉中段。
// 只截前面会把 AI 结尾的引导提问切掉（"你愿意多说说吗？"），三等分又会把语义切碎。
public final class MemoryTextTruncator {

    // 头尾之间的分隔，让模型知道中间有省略
    private static final String ELLIPSIS = "\n…\n";

    // 句子断点：在句末标点之后切分，标点留在前一句
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；!?;])");

    private MemoryTextTruncator() {
    }

    public static String headAndTail(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (maxChars <= 0 || trimmed.length() <= maxChars) {
            return trimmed;
        }

        // 头 60%、尾 40%：整数运算，先乘后除；预算要扣掉省略号，总长才不超过 maxChars
        int budget = maxChars - ELLIPSIS.length();
        if (budget <= 0) {
            return trimmed.substring(0, maxChars);
        }
        int headBudget = budget * 6 / 10;
        int tailBudget = budget - headBudget;
        String[] sentences = SENTENCE_END.split(trimmed);

        // 从头按句累加，装不下就停
        StringBuilder head = new StringBuilder();
        for (String sentence : sentences) {
            if (head.length() + sentence.length() > headBudget) {
                break;
            }
            head.append(sentence);
        }

        // 从尾按句累加，装不下就停
        StringBuilder tail = new StringBuilder();
        for (int i = sentences.length - 1; i >= 0; i--) {
            String sentence = sentences[i];
            if (tail.length() + sentence.length() > tailBudget) {
                break;
            }
            tail.insert(0, sentence);
        }

        // 单句就超过预算时，退回按字符硬切，保证不为空
        if (head.length() == 0) {
            head.append(trimmed, 0, headBudget);
        }
        if (tail.length() == 0) {
            tail.append(trimmed.substring(trimmed.length() - tailBudget));
        }
        return head + ELLIPSIS + tail;
    }
}
