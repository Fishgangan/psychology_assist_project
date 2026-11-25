package com.driedfish.project_psychology_health_assist.enumClass;

import lombok.Getter;

/**
 * 风险等级：数值与文案的唯一来源。
 * riskLevel 与 riskDescription 都从这里取，避免"高危却写无风险"。
 */
@Getter
public enum RiskLevel {
    STABLE(0, "情绪稳定"),
    ATTENTION(1, "需要关注"),
    INTERVENTION(2, "需要心理疏导");

    private final Integer code;
    private final String description;

    RiskLevel(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举。
     * 与用户状态不同，这里的 code 可能来自大模型输出，越界时不抛异常：
     * 高于上限按最高等级处理（风险不可降级），低于下限按最低等级处理。
     */
    public static RiskLevel fromCode(Integer code) {
        if (code == null) {
            return STABLE;
        }
        for (RiskLevel level : RiskLevel.values()) {
            if (level.getCode().equals(code)) {
                return level;
            }
        }
        return code > INTERVENTION.getCode() ? INTERVENTION : STABLE;
    }
}
