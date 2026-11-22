package com.driedfish.project_psychology_health_assist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationMessageMapper;
import com.driedfish.project_psychology_health_assist.service.convert.MessageConvert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsultationMessageService {
    private final ConsultationMessageMapper consultationMessageMapper;

    //保存用户消息
    public ConsultationMessage saveUserMessage(Long sessionId, String content) {
        //构建用户消息实体
        ConsultationMessage userMessage = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(1)
                .messageType(1)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(userMessage);
        return userMessage;
    }

    //保存情绪标签
    public void saveEmotionTag(Long messageId, String emotionTag) {
        //更新情绪标签
        consultationMessageMapper.update(new LambdaUpdateWrapper<ConsultationMessage>()
                .set(ConsultationMessage::getEmotionTag, emotionTag)
                .eq(ConsultationMessage::getId, messageId));
    }

    //保存AI助手消息
    public ConsultationMessage saveAiMessage(Long sessionId, String content, String aiModel) {
        //构建AI助手实体
        ConsultationMessage aiMessage = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(2)
                .messageType(1)
                .content(content)
                .aiModel(aiModel)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(aiMessage);
        return aiMessage;

    }

    //获取会话消息数量
    public Integer getMessageCountBySessionId(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId);
        Long count = consultationMessageMapper.selectCount(queryWrapper);
        return count.intValue();
    }

    //获取对话最后一条消息
    public ConsultationMessageResponseDTO getLastMessageBySessionId(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId)
                .orderByDesc(ConsultationMessage::getCreatedAt)
                .last("limit 1");
        ConsultationMessage lastMessage = consultationMessageMapper.selectOne(queryWrapper);
        return lastMessage != null ? MessageConvert.convertToResponseDTO(lastMessage) : null;


    }

    //删除会话所有消息
    public void deleteMessagesBySessionId(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionId);
        consultationMessageMapper.delete(queryWrapper);
    }

    //获取会话最近limit条消息
    public List<ConsultationMessageResponseDTO> getRecentMessages(Long sessionId, int limit) {
        List<ConsultationMessage> messages = consultationMessageMapper.selectList(
                new LambdaQueryWrapper<ConsultationMessage>()
                        .eq(ConsultationMessage::getSessionId, sessionId)
                        .eq(ConsultationMessage::getSenderType, 1)  // 只查询用户消息
                        .orderByDesc(ConsultationMessage::getId)
                        .last("LIMIT " + Math.max(1, limit)));
        Collections.reverse(messages);   // 转成正序
        return messages.stream().map(MessageConvert::convertToResponseDTO).collect(Collectors.toList());
    }
}