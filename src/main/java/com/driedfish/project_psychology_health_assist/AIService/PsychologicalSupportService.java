package com.driedfish.project_psychology_health_assist.AIService;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.service.ConsultationMessageService;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import com.driedfish.project_psychology_health_assist.service.EmotionAnalysisService;
import com.driedfish.project_psychology_health_assist.util.SessionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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

@Slf4j
@Service
public class PsychologicalSupportService {
    @Value("${spring.ai.open-ai.chat.options.model:qwen3.8-27b}")
    private String currentAiModel;
    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final ConsultationSessionService consultationSessionService;
    private final ConsultationMessageService consultationMessageService;
    private final EmotionAnalysisService emotionAnalysisService;
    public PsychologicalSupportService(
            @Qualifier("chat") ChatClient chatClient,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            ConsultationSessionService consultationSessionService,
            ConsultationMessageService consultationMessageService,
            EmotionAnalysisService emotionAnalysisService
               ){

        this.chatClient=chatClient;
        this.questionAnswerAdvisor=questionAnswerAdvisor;
        this.consultationSessionService=consultationSessionService;
        this.consultationMessageService=consultationMessageService;
        this.emotionAnalysisService=emotionAnalysisService;

    }

    //开始会话
    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 会话创建逻辑
        ConsultationSession consultationSession = consultationSessionService.createSession(userId, createDTO);
        //将初始用户消息保存到Message表
        consultationMessageService.saveUserMessage(consultationSession.getId(), createDTO.getInitialMessage());
        //创建会话信息
        String sessionId="session_"+consultationSession.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getSessionTitle(),
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis()+86400000L,//会话过期时间24h
                1L,
                "ACTIVE"

        );


    }
    //流式对话
    public Flux<String> streamPsychologicalChat(String sessionId,String userMessage,Long userId) {

        Long dbSessionId = SessionUtils.extractSessionId(sessionId);
        String conversationId="conversation_"+sessionId;

        //用Mono.fromCallable创建响应流 把SQL操作放到Callable中，防止阻塞主线程，导致响应延迟
        return Mono.fromCallable(()->{
            //检查会话是否属于当前用户
            consultationSessionService.checkSessionOwner(dbSessionId,userId);
            Long userMessageId = null;
            //是否是初始消息
            boolean isInitialMessage=false;
            //检查会话是否存在，避免重复保存
            Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
            if(messageCount==1){
                ConsultationMessageResponseDTO lastMessage = consultationMessageService.getLastMessageBySessionId(dbSessionId);
                if(lastMessage!=null&&lastMessage.getSenderType()==1&&userMessage.equals(lastMessage.getContent())){
                    isInitialMessage=true;
                    userMessageId = lastMessage.getId();
                }
            }
            if(!isInitialMessage){
                //保存用户消息到Message表
                userMessageId = consultationMessageService.saveUserMessage(dbSessionId, userMessage).getId();
            }
            return userMessageId;})//返回占位符，等待流处理
            .subscribeOn(Schedulers.boundedElastic())
                //用flatMapMany创建响应流
                .flatMapMany(userMessageId ->{
                    StringBuilder aiResponse=new StringBuilder();
                    //调用AI助手
                    return chatClient.prompt()
                                .user(userMessage)
                                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID,conversationId).advisors(questionAnswerAdvisor))
                                .stream()
                                .content()
                            // 处理流式响应
                                .doOnNext(fragment-> aiResponse.append(fragment))
                                .doOnComplete(()->{
                                    String completeRes=aiResponse.toString();
                                    if(StringUtils.hasText(completeRes)){
                                        //用Mono.fromRunnable创建响应流 把SQL操作放到Runnable中，防止阻塞主线程，导致响应延迟
                                        Mono.fromRunnable(()->{
                                            ConsultationMessage message = consultationMessageService.saveAiMessage(dbSessionId, completeRes, currentAiModel);
                                            //分析用户情绪
                                            String emotionTag = emotionAnalysisService.updateMessageEmotionTag(userMessageId, userMessage);

                                            //更新会话情绪分析(json)
                                            emotionAnalysisService.updateSessionEmotionAnalysis(dbSessionId, userMessage, emotionTag);

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
    public void deleteSession(String sessionId,Long userId) {
        Long dbSessionId = SessionUtils.extractSessionId(sessionId);
        //检查会话是否属于当前用户
        consultationSessionService.checkSessionOwner(dbSessionId,userId);
        consultationMessageService.deleteMessagesBySessionId(dbSessionId);
        consultationSessionService.deleteSession(dbSessionId);
    }


}
