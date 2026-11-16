package com.driedfish.project_psychology_health_assist.AIService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
@Component
public class redisChatMemory implements ChatMemory {
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "chat_memory:";
    private static final int MAX_MESSAGES = 30;
    private static final Duration TTL = Duration.ofDays(7);
    public redisChatMemory(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        List<String> stringMessages=messages.stream()
                .map(m->m.getMessageType()+"|"+m.getText())
                .toList();
        redisTemplate.opsForList().rightPushAll(key, stringMessages);
        redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
        redisTemplate.expire(key, TTL);
    }
    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> stringMessages=redisTemplate.opsForList().range(key, 0, -1);
        if(stringMessages==null||stringMessages.isEmpty()){
            return new ArrayList<>();
        }
        List<Message> messages=new ArrayList<>();
        for(String s:stringMessages){
            String[] parts=s.split("\\|",2);
            if(parts.length==2){
                MessageType type=MessageType.valueOf(parts[0]);
                String text=parts[1];
                if (type==MessageType.USER){
                    messages.add(new UserMessage(text));
                }
                else if (type==MessageType.ASSISTANT){
                        messages.add(new AssistantMessage(text));
                    }
            }
        }
        return messages;
    }
    @Override
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
    }
}
