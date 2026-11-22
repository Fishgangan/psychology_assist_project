package com.driedfish.project_psychology_health_assist.AIService;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RiskAssessor {

    private static final Set<String> HIGH_RISK = Set.of(
            "自杀", "自残", "不想活", "结束生命", "轻生", "了结自己",
            "跳楼", "割腕", "上吊", "安眠药", "遗书", "告别世界", "活不下去", "去死");

    private static final Set<String> MEDIUM_RISK = Set.of(
            "绝望", "没意义", "没有希望", "撑不住", "生无可恋", "不想面对", "崩溃");
    /** 高危情绪前兆 */
    private static final Set<String> HIGH_RISK_EMOTIONS = Set.of(
            "绝望", "无望", "自弃", "求死", "轻生");

    /** 中危负面情绪 */
    private static final Set<String> MEDIUM_RISK_EMOTIONS = Set.of(
            "痛苦", "抑郁", "低落", "悲伤", "焦虑", "愤怒", "恐惧", "孤独", "崩溃", "无助");

    public int assess(String message) {
        if (message == null || message.isBlank()) {
            return 0;
        }
        if (containsAny(message, HIGH_RISK)) {
            return 2;
        }
        if (containsAny(message, MEDIUM_RISK)) {
            return 1;
        }
        return 0;
    }
    public int assessByEmotion(String emotionTag) {
        if (emotionTag == null) return 0;
        if (containsAny(emotionTag, HIGH_RISK_EMOTIONS)) return 2;
        if (containsAny(emotionTag, MEDIUM_RISK_EMOTIONS)) return 1;
        return 0;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
