package com.driedfish.project_psychology_health_assist.service;
import com.driedfish.project_psychology_health_assist.AIService.PromptManage;
import com.driedfish.project_psychology_health_assist.AIService.RiskAssessor;
import com.driedfish.project_psychology_health_assist.common.DTO.internal.EmotionResult;
import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.enumClass.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
@Slf4j
@Service
public class EmotionAnalysisService {
    private final ConsultationMessageService consultationMessageService;
    private final ChatClient emotionTagClient;
    private final ChatClient emotionCompleteClient;
    private final RiskAssessor riskAssessor;
    private final ObjectMapper objectMapper;
    private final ConsultationSessionService consultationSessionService;
    public EmotionAnalysisService(
            ConsultationMessageService consultationMessageService,
            @Qualifier("emotionTag") ChatClient emotionTagClient,
            @Qualifier("emotionComplete") ChatClient emotionCompleteClient,
            RiskAssessor riskAssessor,
            ObjectMapper objectMapper,
            ConsultationSessionService consultationSessionService
    ) {
        this.objectMapper=objectMapper;
        this.consultationSessionService = consultationSessionService;
        this.consultationMessageService = consultationMessageService;
        this.riskAssessor = riskAssessor;
        this.emotionTagClient = emotionTagClient;
        this.emotionCompleteClient = emotionCompleteClient;

    }
    // 更新用户情绪标签
    public String updateMessageEmotionTag(Long messageId, String userMessage) {
        try{
        String emotionTag = emotionTagClient.prompt()
                .user(PromptManage.EMOTION_ANALYSIS_PROMPT+"\n"+userMessage)
                .call()
                .content();
        if(StringUtils.hasText(emotionTag)){
            emotionTag=emotionTag.trim();
            consultationMessageService.saveEmotionTag(messageId, emotionTag);
            log.info("用户情绪分析结果：{}",emotionTag);
            return emotionTag;
        }}catch (Exception e){
            log.warn("用户情绪分析失败",e);
        }
        return null;
    }


    // 更新会话情绪分析
    public void updateSessionEmotionAnalysis(Long dbSessionId, String userMessage, String emotionTag) {
        try {
            List<ConsultationMessageResponseDTO> recent =
                    consultationMessageService.getRecentMessages(dbSessionId, 10);

            if (recent == null || recent.isEmpty()) return;

            String content = emotionCompleteClient.prompt()
                    .user(buildEmotionAnalysisPrompt(recent))
                    .call()
                    .content();

            EmotionResult analysis = parseEmotionJson(content);
            if (analysis == null) {
                log.warn("情绪分析 JSON 解析失败，原始返回：{}", content);
                return;
            }

            // 规则引擎兜底 riskLevel
            int riskLevelMessage = riskAssessor.assess(userMessage);
            int riskLevelEmotion = Math.max(riskAssessor.assessByEmotion(emotionTag),
                    riskAssessor.assessByEmotion(analysis.getPrimaryEmotion()));
            int riskModelLevel = analysis.getRiskLevel() == null ? 0 : analysis.getRiskLevel();
            int riskLevel = Math.max(Math.max(riskLevelMessage, riskLevelEmotion), riskModelLevel);
            if (Boolean.TRUE.equals(analysis.getIsNegative()) && riskLevel == 0) {
                riskLevel = 1;
            }
            // 数值与文案取自同一个枚举常量，保证二者同源（模型越界值由 fromCode 收敛）
            RiskLevel riskLevelEnum = RiskLevel.fromCode(riskLevel);
            analysis.setRiskLevel(riskLevelEnum.getCode());
            analysis.setRiskDescription(riskLevelEnum.getDescription());
            analysis.setTimestamp(System.currentTimeMillis());

            consultationSessionService.addLastEmotionAnalysis(
                    dbSessionId, objectMapper.writeValueAsString(analysis));
            log.info("session 情绪分析：{}", analysis);
        }catch (Exception e){
            log.warn("session 情绪总结失败",e);

    }}
    // 构建情绪分析提示
    private String buildEmotionAnalysisPrompt(List<ConsultationMessageResponseDTO> recent) {
        StringBuilder sb = new StringBuilder(PromptManage.EMOTION_COMPLETE_ANALYSIS_PROMPT);
        for (ConsultationMessageResponseDTO msg : recent) {
            String tag = StringUtils.hasText(msg.getEmotionTag()) ? msg.getEmotionTag() : "未标注";
            sb.append("[").append(tag).append("] ")
                    //减少消息长度，避免过度消耗模型资源
                    .append(StringUtils.truncate(msg.getContent(), 60))
                    .append("\n");
        }
        return sb.toString();
    }
    //防止模型返回的JSON字符串包含多余文字，日后强制要求模型返回JSON格式
    private EmotionResult parseEmotionJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            json = json.substring(start, end + 1);   // 剥离可能的 ```json 围栏和多余文字
            return objectMapper.readValue(json, EmotionResult.class);
        } catch (Exception e) {
            log.warn("情绪总结 JSON 解析失败", e);
            return null;
        }
    }
}
