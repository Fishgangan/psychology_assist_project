package com.driedfish.project_psychology_health_assist.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RagDataInitializer implements CommandLineRunner {
    private final VectorStore vectorStore;
    public RagDataInitializer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    @Override
    public void run(String... args) throws Exception {
        log.info("=====开始初始化文档=====");
        Resource resource = new ClassPathResource("data/psychology_data.txt");
        if(!resource.exists()){
            log.error("文件不存在");
            return;
        }
        //spring-ai此版本不支持自定义重叠大小，在此手动实现
        TextReader textReader = new TextReader(resource);
        List<Document> rawDocuments = textReader.get();
        StringBuilder fullTextBuilder=new StringBuilder();
        for(Document document:rawDocuments){
            fullTextBuilder.append(document.getText()).append("\n");
        }
        String fullText=fullTextBuilder.toString();
        List<Document> customChunks=new ArrayList<>();
        int chunkSize=300;
        int minChunkSizeChars=10;
        int overlap=50;
        for(int i=0;i<fullText.length();i+=chunkSize-overlap){
            int end=Math.min(i+chunkSize,fullText.length());
            String chunk=fullText.substring(i,end);
            if(chunk.trim().length()>minChunkSizeChars){
            Document newDoc=new Document(chunk);
            customChunks.add(newDoc);}
            if(end==fullText.length()){
                break;
            }
        }
        vectorStore.add(customChunks);
        log.info("=====文档初始化完成=====");
    }
}
