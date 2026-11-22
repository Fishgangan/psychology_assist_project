package com.driedfish.project_psychology_health_assist.common.DTO.internal;
import lombok.Data;

import java.util.List;

@Data
public class EmotionResult {
        private String icon;                        // 表情 emoji
        private String label;                       // 简短标签
        private List<String> keywords;              // 关键词
        private Integer riskLevel;                  // 0/1/2
        private String riskDescription;             // 风险描述
        private Long timestamp;                     // 毫秒时间戳
        private Boolean isNegative;                 // 是否负面
        private String suggestion;                  // 安抚建议
        private Integer emotionScore;               // 0~100
        private String primaryEmotion;              // 主要情绪
        private List<String> improvementSuggestions;// 改善建议


}
