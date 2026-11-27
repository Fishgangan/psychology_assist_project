package com.driedfish.project_psychology_health_assist.rag;

import com.driedfish.project_psychology_health_assist.rag.chunk.DocumentChunker;
import com.driedfish.project_psychology_health_assist.rag.chunk.MarkdownHeadingChunker;
import com.driedfish.project_psychology_health_assist.rag.chunk.QaChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 入库：扫描资源目录 → 按类型选切块策略 → 写入向量库。
 *
 * <p>目录约定：</p>
 * <ul>
 *   <li>{@code data/rag/**}{@code /*.md} —— 权威文档（WHO 等），按标题切块；</li>
 *   <li>{@code data/*.txt} —— 结构化问答语料，按问答切块。</li>
 * </ul>
 *
 * <p>幂等：切块器按 {@code source#序号} 生成固定 id，重复启动是覆盖而非追加。</p>
 */
@Slf4j
@Component
public class RagIngestionService {

    private static final String MARKDOWN_PATTERN = "classpath*:data/rag/**/*.md";
    private static final String QA_PATTERN = "classpath*:data/*.txt";

    private final VectorStore vectorStore;
    private final MarkdownHeadingChunker markdownChunker;
    private final QaChunker qaChunker;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public RagIngestionService(VectorStore vectorStore,
                               MarkdownHeadingChunker markdownChunker,
                               QaChunker qaChunker) {
        this.vectorStore = vectorStore;
        this.markdownChunker = markdownChunker;
        this.qaChunker = qaChunker;
    }

    /**
     * 加载全部语料并写入向量库。
     *
     * @return 写入的块数
     */
    public int ingestAll() {
        List<Document> documents = new ArrayList<>();
        documents.addAll(load(MARKDOWN_PATTERN, markdownChunker));
        documents.addAll(load(QA_PATTERN, qaChunker));
        if (documents.isEmpty()) {
            log.warn("RAG 入库：没有找到任何语料，跳过");
            return 0;
        }
        vectorStore.add(documents);
        log.info("RAG 入库完成：{} 块", documents.size());
        return documents.size();
    }

    private List<Document> load(String pattern, DocumentChunker chunker) {
        List<Document> result = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(pattern);
            Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            for (Resource resource : resources) {
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("source", resource.getFilename());
                List<Document> chunks = chunker.split(text, metadata);
                result.addAll(chunks);
                log.info("载入 {}：{} 字 → {} 块", resource.getFilename(), text.length(), chunks.size());
            }
        } catch (IOException e) {
            log.warn("RAG 语料读取失败：{}", pattern, e);
        }
        return result;
    }
}
