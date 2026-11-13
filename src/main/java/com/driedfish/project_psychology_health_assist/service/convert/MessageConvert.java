package com.driedfish.project_psychology_health_assist.service.convert;

import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;

public class MessageConvert {
    public static ConsultationMessageResponseDTO convertToResponseDTO(ConsultationMessage message) {
        if(message == null) {
            return null;
        }
        String content = message.getContent();
        int contentLength = content==null?0:content.length();
        return ConsultationMessageResponseDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderType(message.getSenderType())
                .messageType(message.getMessageType())
                .content(content)
                .emotionTag(message.getEmotionTag())
                .aiModel(message.getAiModel())
                .createdAt(message.getCreatedAt())
                .contentLength(contentLength)
                .build();
    }
}
