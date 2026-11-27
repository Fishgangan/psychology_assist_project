package com.driedfish.project_psychology_health_assist.rag.chunk;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答切块：按「问：」边界切开，一组问答一块。
 *
 * <p>只适用于结构化问答数据（如当前测试语料、PsyQA）。换成长文时改用
 * {@link MarkdownHeadingChunker}，加载流程不需要改。</p>
 */
@Component
public class QaChunker implements DocumentChunker {

    @Override
    public List<Document> split(String text, Map<String, Object> metadata) {
        String source = String.valueOf(metadata.getOrDefault("source", "unknown"));
        List<Document> docs = new ArrayList<>();
        int index = 0;
        for (String block : text.split("(?=问：)")) {
            String trimmed = block.trim();
            if (trimmed.length() < 20) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>(metadata);
            meta.put("source", source);
            meta.put("title", firstQuestion(trimmed));
            docs.add(new Document(source + "#" + index, trimmed, meta));
            index++;
        }
        return docs;
    }

    private String firstQuestion(String block) {
        int end = block.indexOf('\n');
        String question = end < 0 ? block : block.substring(0, end);
        return question.length() > 40 ? question.substring(0, 40) : question;
    }
}
