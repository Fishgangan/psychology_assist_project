package com.driedfish.project_psychology_health_assist.AIService;

import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;

import java.util.List;

public class StructOutPut {
    //record 定义结构体数据类型,record可自动实现equals,hashCode,toString等方法
    public record StreamChatSession(String sessionId,
                                    Long userHash,
                                    String initialMessage,
                                    Long startTime,
                                    Long expiryTime,
                                    Long messageCount,
                                    String status) {}
    public record StreamChatSessionList(List<StreamChatSession> sessions,Long total) {}
    public record StreamChatMessageList(List<ConsultationMessage> messages, Long total) {}
}
