package com.driedfish.project_psychology_health_assist.AIService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.service.ConsultationMessageService;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import com.driedfish.project_psychology_health_assist.util.SessionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class PsychologicalSupportService {
    @Qualifier("open-ai")
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private ConsultationSessionService consultationSessionService;
    @Autowired
    private ConsultationMessageService consultationMessageService;

    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 会话创建逻辑
        ConsultationSession consultationSession = consultationSessionService.createSession(userId, createDTO);
        //将初始用户消息保存到Message表
        ConsultationMessage userMessage = consultationMessageService.saveUserMessage(consultationSession.getId(), createDTO.getInitialMessage(), null);
        //创建会话信息
        String sessionId="session_"+consultationSession.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis()+86400000L,//会话过期时间24h
                1L,
                "ACTIVE"

        );


    }
    public Flux<String> streamPsychologicalChat(String sessionId,String userMessage) {
        //创建响应流
        return Flux.create(sink->{
            Long dbSessionId = SessionUtils.extractSessionId(sessionId);
            //是否是初始消息
            boolean isInitialMessage=false;
            //检查会话是否存在，避免重复保存
            Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
            if(messageCount==1){
                ConsultationMessageResponseDTO lastMessage = consultationMessageService.getLastMessageBySessionId(dbSessionId);
                if(lastMessage!=null&&lastMessage.getSenderType()==1&&userMessage.equals(lastMessage.getContent())){
                    isInitialMessage=true;
                }
            }
            if(!isInitialMessage){
                //保存用户消息到Message表
                 consultationMessageService.saveUserMessage(dbSessionId, userMessage, null);


            }
            //流式响应AI助手消息
            //生成对话记忆管理
            String conversationId="conversation_"+sessionId;
            //构建系统提示
            List<Message> userMessages=new ArrayList<>();
            userMessages.add(new UserMessage(userMessage));
            chatMemory.add(conversationId,userMessages);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)));
            //存储ai完整响应
            StringBuilder aiResponse=new StringBuilder();
            //调用AI助手
            chatClient.prompt(prompt)
                    .user(userMessage)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID,conversationId))
                    .stream()
                    .content()
                    .doOnNext(Fragment->{
                        aiResponse.append(Fragment);
                        sink.next(Fragment);
                    })
                    .doOnComplete(()->{
                        String completeRes=aiResponse.toString();
                        consultationMessageService.saveAiMessage(dbSessionId, completeRes, "open-ai");
                        //添加ai助手消息到对话记忆管理
                        List<Message> aiMessages=new ArrayList<>();
                        aiMessages.add(new AssistantMessage(completeRes));
                        chatMemory.add(conversationId,aiMessages);
                        sink.complete();
                    })
                    .doOnError(sink::error)
                    .subscribe();//订阅并启动流




        });
    }

    //删除会话
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        Long dbSessionId = SessionUtils.extractSessionId(sessionId);
        consultationSessionService.deleteSession(dbSessionId);
        consultationMessageService.deleteMessagesBySessionId(dbSessionId);
    }


}
