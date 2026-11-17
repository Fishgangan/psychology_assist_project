package com.driedfish.project_psychology_health_assist.config;
import com.driedfish.project_psychology_health_assist.AIService.PromptManage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {


    @Bean("chat")
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
    @Bean("emotion")
    public ChatClient emotionChatClient(ChatClient.Builder builder,
                                        @Value("${spring.ai.open-ai.emotion.model:qwen-3.8-27b}") String emotionAiModel) {
        return builder
                .defaultOptions(ChatOptions.builder().model(emotionAiModel).build())
                .build();
    }
}
