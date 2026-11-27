package com.driedfish.project_psychology_health_assist.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

 //启动时灌一次 RAG 语料。
 //入库失败只记日志，不阻断应用启动（对话能力不该被知识库拖死）
@Slf4j
@Component
public class RagDataInitializer implements CommandLineRunner {

    private final RagIngestionService ingestionService;

    public RagDataInitializer(RagIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(String... args) {
        log.info("=====开始初始化 RAG 文档=====");
        try {
            ingestionService.ingestAll();
        } catch (Exception e) {
            log.error("RAG 初始化失败，跳过本次入库", e);
        }
        log.info("=====RAG 文档初始化结束=====");
    }
}
