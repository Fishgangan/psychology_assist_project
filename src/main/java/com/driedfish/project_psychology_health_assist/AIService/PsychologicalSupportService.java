package com.driedfish.project_psychology_health_assist.AIService;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.service.ConsultationMessageService;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import com.driedfish.project_psychology_health_assist.util.SessionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
@Slf4j
@Service
public class PsychologicalSupportService {
    @Qualifier("open-ai")
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private ChatMemory chatMemory;
    @Value("${spring.ai.open-ai.chat.options.model:qwen-turbo}")
    private String currentAiModel;
    @Autowired
    private ConsultationSessionService consultationSessionService;
    @Autowired
    private ConsultationMessageService consultationMessageService;
    //开始会话
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
    //流式对话
    public Flux<String> streamPsychologicalChat(String sessionId,String userMessage) {
        Long dbSessionId = SessionUtils.extractSessionId(sessionId);
        String conversationId="conversation_"+sessionId;
        //用Mono.fromCallable创建响应流 把SQL操作放到Callable中，防止阻塞主线程，导致响应延迟
        return Mono.fromCallable(()->{
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
            //把用户消息添加到对话记忆管理
            List<Message> userMessages=new ArrayList<>();
            userMessages.add(new UserMessage(userMessage));
            chatMemory.add(conversationId,userMessages);
            return true;})//返回占位符，等待流处理
            .subscribeOn(Schedulers.boundedElastic())
                //用flatMapMany创建响应流
                .flatMapMany(isReady ->{
                    StringBuilder aiResponse=new StringBuilder();
                    Prompt prompt =new Prompt(List.of(
                            new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)));
                    //调用AI助手
                    return chatClient.prompt(prompt)
                                .user(userMessage)
                                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID,conversationId))
                                .stream()
                                .content()
                            // 处理流式响应
                                .doOnNext(fragment-> aiResponse.append(fragment))
                                .doOnComplete(()->{
                                    String completeRes=aiResponse.toString();
                                    if(StringUtils.hasText(completeRes)){
                                        //用Mono.fromCallable创建响应流 把SQL操作放到Callable中，防止阻塞主线程，导致响应延迟
                                        Mono.fromRunnable(()->{
                                            consultationMessageService.saveAiMessage(dbSessionId, completeRes, currentAiModel);
                                            //添加ai助手消息到对话记忆管理
                                            List<Message> aiMessages=new ArrayList<>();
                                            aiMessages.add(new AssistantMessage(completeRes));
                                            chatMemory.add(conversationId,aiMessages);
                                        }).subscribeOn(Schedulers.boundedElastic())
                                                .doOnError(error->{
                                                    log.error("保存AI助手消息到Message表失败",error);
                                                })
                                                .subscribe();}})


                                .doOnError(error->{
                                    if(error instanceof IOException){
                                        log.info("用户主动断开连接，关闭流");
                                    }else{
                                        log.error("AI助手调用失败",error);
                                    }
                                })
                            //客户端断连时，关闭流
                            .doFinally(signalType->{
                                if(signalType==SignalType.CANCEL){
                                    log.info("客户端断连，关闭流");
                                } else if (signalType== SignalType.ON_COMPLETE) {
                                    log.info("AI助手调用正常结束。sessionId={}",sessionId);
                                }
                            });
                }  );


    }
    //删除会话
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        Long dbSessionId = SessionUtils.extractSessionId(sessionId);
        consultationMessageService.deleteMessagesBySessionId(dbSessionId);
        consultationSessionService.deleteSession(dbSessionId);
    }


}
