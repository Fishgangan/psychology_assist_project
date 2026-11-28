package com.driedfish.project_psychology_health_assist.config;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    //心理对话
    @Bean("chat")
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor(10))   // order=10 > RAG 的 0,调试用，注释掉后正常运行
                .build();
    }
    //情绪标签
    @Bean("emotionTag")
    public ChatClient emotionChatClient(ChatClient.Builder builder,
                                        @Value("${spring.ai.open-ai.emotion.model:qwen3.8-flash}") String emotionAiModel) {
        return builder
                .defaultOptions(ChatOptions.builder().model(emotionAiModel).build())
                .build();
    }
    //情绪分析（json）
    @Bean("emotionComplete")
    public ChatClient emotionCompleteChatClient(ChatClient.Builder builder,
                                                @Value("${spring.ai.open-ai.emotionComplete.model:qwen3.8-flash}") String emotionCompleteAiModel) {
        return builder
                .defaultOptions(ChatOptions.builder().model(emotionCompleteAiModel).build())
                .build();
    }
    //记忆总结
    @Bean("memoryCompact")
    public ChatClient memoryCompactChatClient(ChatClient.Builder builder,
                                                @Value("${spring.ai.open-ai.memoryCompact.model:qwen3.8-flash}") String memoryCompactAiModel) {
        return builder
                .defaultOptions(ChatOptions.builder().model(memoryCompactAiModel).build())
                .build();
    }
    //回合前置判定（风险 + 是否检索）
    @Bean("turnScreen")
    public ChatClient turnScreenChatClient(ChatClient.Builder builder,
                                           @Value("${spring.ai.open-ai.turnScreen.model:qwen3.8-flash}") String turnScreenAiModel) {
        return builder
                .defaultOptions(ChatOptions.builder().model(turnScreenAiModel).build())
                .build();
    }
}
