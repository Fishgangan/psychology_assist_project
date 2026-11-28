package com.driedfish.project_psychology_health_assist.AIService;

import com.driedfish.project_psychology_health_assist.rag.RagGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// 回合前置判定：一次轻量模型调用，同时产出「风险等级」「要不要检索」「检索用的中文查询词」。
// 为什么不用关键词：词表覆盖不了外语、隐晦表达、第三人称叙述（"我室友说他不想活了"）。
// 为什么放在生成之前：现有风险分级是流结束后才跑，对当轮回复没有约束力。
// 失败时回落到关键词（RiskAssessor + RagGate），并用原话当查询词。
@Slf4j
@Service
public class TurnScreenService {

    public record TurnScreen(int risk, boolean needKnowledge, String searchQuery, boolean selfRelated) {
    }

    private final ChatClient turnScreenClient;
    private final RiskAssessor riskAssessor;
    private final RagGate ragGate;
    private final ObjectMapper objectMapper;

    public TurnScreenService(@Qualifier("turnScreen") ChatClient turnScreenClient,
                             RiskAssessor riskAssessor,
                             RagGate ragGate,
                             ObjectMapper objectMapper) {
        this.turnScreenClient = turnScreenClient;
        this.riskAssessor = riskAssessor;
        this.ragGate = ragGate;
        this.objectMapper = objectMapper;
    }

    public TurnScreen screen(String userMessage) {
        TurnScreen result = doScreen(userMessage);
        log.info("前置判定：risk={} needKnowledge={} selfRelated={} searchQuery={}",
                result.risk(), result.needKnowledge(), result.selfRelated(), result.searchQuery());
        return result;
    }

    private TurnScreen doScreen(String userMessage) {
        try {
            String content = turnScreenClient.prompt()
                    .user(PromptManage.TURN_SCREEN_PROMPT + "\n用户发言：\n" + userMessage)
                    .call()
                    .content();
            TurnScreen parsed = parse(content, userMessage);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("前置判定调用失败，回落关键词", e);
        }
        // 回落：词表判定，查询词用原话；判定失败时保守当作"涉及自身"，照常做情绪分析
        return new TurnScreen(riskAssessor.assess(userMessage), ragGate.shouldRetrieve(userMessage), userMessage, true);
    }

    private TurnScreen parse(String content, String userMessage) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            TurnScreen screen = objectMapper.readValue(content.substring(start, end + 1), TurnScreen.class);
            // 模型可能给出越界值，收敛到 0~2
            int risk = Math.max(0, Math.min(2, screen.risk()));
            // 需要检索但没给查询词时，退回原话
            String query = StringUtils.hasText(screen.searchQuery()) ? screen.searchQuery() : userMessage;
            return new TurnScreen(risk, screen.needKnowledge(), query, screen.selfRelated());
        } catch (Exception e) {
            log.warn("前置判定 JSON 解析失败：{}", content);
            return null;
        }
    }
}
