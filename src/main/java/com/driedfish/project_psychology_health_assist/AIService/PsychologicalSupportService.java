package com.driedfish.project_psychology_health_assist.AIService;
import com.driedfish.project_psychology_health_assist.AIService.memory.ConversationMemoryService;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.ConsultationMessageResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.rag.RagContextService;
import com.driedfish.project_psychology_health_assist.service.ConsultationMessageService;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import com.driedfish.project_psychology_health_assist.service.EmotionAnalysisService;
import com.driedfish.project_psychology_health_assist.util.SessionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
import java.util.List;

@Slf4j
@Service
public class PsychologicalSupportService {
    @Value("${spring.ai.open-ai.chat.options.model:qwen3.8-27b}")
    private String currentAiModel;
    private final ChatClient chatClient;
    private final RagContextService ragContextService;
    private final ConsultationSessionService consultationSessionService;
    private final ConsultationMessageService consultationMessageService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final ConversationMemoryService conversationMemoryService;
    private final TurnScreenService turnScreenService;
    public PsychologicalSupportService(
            @Qualifier("chat") ChatClient chatClient,
            RagContextService ragContextService,
            ConsultationSessionService consultationSessionService,
            ConsultationMessageService consultationMessageService,
            EmotionAnalysisService emotionAnalysisService,
            ConversationMemoryService conversationMemoryService,
            TurnScreenService turnScreenService
               ){

        this.chatClient=chatClient;
        this.ragContextService=ragContextService;
        this.consultationSessionService=consultationSessionService;
        this.consultationMessageService=consultationMessageService;
        this.emotionAnalysisService=emotionAnalysisService;
        this.conversationMemoryService=conversationMemoryService;
        this.turnScreenService=turnScreenService;

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
        String conversationId = SessionUtils.conversationId(sessionId);

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
                    // 前置判定：一次小模型调用拿到「风险」「是否需要知识」「检索词」
                    TurnScreenService.TurnScreen screen = turnScreenService.screen(userMessage);
                    String knowledge = screen.needKnowledge()
                            ? ragContextService.buildContext(userMessage, screen.searchQuery())
                            : null;
                    List<Message> messages = conversationMemoryService.prepare(conversationId, dbSessionId, userMessage);
                    if (screen.risk() >= 2) {
                        messages = appendCrisisDirective(messages);
                    }
                    if (StringUtils.hasText(knowledge)) {
                        messages = appendKnowledge(messages, knowledge);
                    }
                    return chatClient.prompt()
                                .messages(messages)
                                .stream()
                                .content()
                            // 处理流式响应
                                .doOnNext(fragment-> aiResponse.append(fragment))
                                .doOnComplete(()->{
                                    String completeRes=aiResponse.toString();
                                        //记录本轮 AI 回复（流结束后调用）
                                        //用Mono.fromRunnable创建响应流 把SQL操作放到Runnable中，防止阻塞主线程，导致响应延迟
                                        Mono.fromRunnable(()->{
                                            if(StringUtils.hasText(completeRes)){
                                                conversationMemoryService.record(conversationId, completeRes);
                                                consultationMessageService.saveAiMessage(dbSessionId, completeRes, currentAiModel);}
                                            //分析用户情绪：只有涉及自身状态时才做，纯知识问答跳过
                                            if (screen.selfRelated()) {
                                                String emotionTag = emotionAnalysisService.updateMessageEmotionTag(userMessageId, userMessage);
                                                //更新会话情绪分析(json)
                                                emotionAnalysisService.updateSessionEmotionAnalysis(dbSessionId, userMessage, emotionTag);
                                            }
                                            conversationMemoryService.compactIfNeeded(conversationId, dbSessionId);


                                        }
                                        ).subscribeOn(Schedulers.boundedElastic())
                                                .doOnError(error->{
                                                    log.error("保存AI助手消息到Message表失败",error);
                                                })
                                                .subscribe();

                                        }
                                        )


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

    // 前置判定为高风险时，把危机提示追加到 system 提示词末尾（保持只有一条 system）
    private List<Message> appendCrisisDirective(List<Message> messages) {
        if (messages.isEmpty() || !(messages.get(0) instanceof SystemMessage system)) {
            return messages;
        }
        messages.set(0, new SystemMessage(system.getText() + PromptManage.CRISIS_ROUND_DIRECTIVE));
        return messages;
    }

    // 把参考资料拼到当前用户消息后面（最后一条一定是 appendCurrentUserMessage 加的那句）
    private List<Message> appendKnowledge(List<Message> messages, String knowledge) {
        int lastIndex = messages.size() - 1;
        if (lastIndex < 0 || !(messages.get(lastIndex) instanceof UserMessage userMessage)) {
            return messages;
        }
        messages.set(lastIndex, new UserMessage(userMessage.getText() + "\n\n" + knowledge));
        return messages;
    }

    //删除会话
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId,Long userId) {
        Long dbSessionId = SessionUtils.extractSessionId(sessionId);
        //检查会话是否属于当前用户
        consultationSessionService.checkSessionOwner(dbSessionId,userId);
        consultationMessageService.deleteMessagesBySessionId(dbSessionId);
        consultationSessionService.deleteSession(dbSessionId);
        conversationMemoryService.clear(SessionUtils.conversationId(sessionId));
    }


}
