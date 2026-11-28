package com.driedfish.project_psychology_health_assist.rag;

import com.driedfish.project_psychology_health_assist.AIService.RiskAssessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

// 检索门控：这一轮要不要注入 RAG 知识。
// 为什么需要：现在每轮无条件塞 3 条，闲聊（"我好累"）也会被塞进"抑郁症症状"，
// 既烧 token 又把回答带偏。阈值解决不了——闲聊和知识文档在语义上就是近的。
// 规则：危机不检索（别稀释话术）；出现知识型信号词才检索；其余交给模型与记忆。
// 纯函数，可单测；规则与语言无关，Python 版可平移。
@Component
public class RagGate {

    // 知识型提问的信号词，命中任意一个才查知识库
    private static final Set<String> KNOWLEDGE_HINTS = Set.of(
            "症状", "表现", "治疗", "诊断", "原因", "副作用", "预防", "缓解",
            "是什么", "什么是", "为什么", "怎么办", "怎么治", "区别", "判断",
            "标准", "算不算", "是不是病", "正常吗", "多久", "疗法", "药物", "心理治疗");

    private final RiskAssessor riskAssessor;

    public RagGate(RiskAssessor riskAssessor) {
        this.riskAssessor = riskAssessor;
    }

    public boolean shouldRetrieve(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String message = userMessage.strip();
        // 危机场景不检索：走固定干预话术
        if (riskAssessor.assess(message) >= 2) {
            return false;
        }
        return KNOWLEDGE_HINTS.stream().anyMatch(message::contains);
    }
}
