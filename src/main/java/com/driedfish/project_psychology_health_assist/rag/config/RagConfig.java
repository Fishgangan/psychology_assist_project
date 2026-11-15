package com.driedfish.project_psychology_health_assist.rag.config;

import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RagConfig {
    @Value("${spring.data.redis.host}")
    private  String redisHost ;
    @Value("${spring.data.redis.port}")
    private int redisPort ;
    @Bean
    public JedisPooled jedisPooled(){
        return new JedisPooled(redisHost,redisPort);
    }
    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel){
        return RedisVectorStore.builder(jedisPooled,embeddingModel)
                .initializeSchema(true)
                .build();
    }
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return new QuestionAnswerAdvisor(vectorStore);
    }
}
