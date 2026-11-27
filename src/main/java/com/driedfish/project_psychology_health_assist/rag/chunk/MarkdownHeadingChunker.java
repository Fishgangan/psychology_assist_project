package com.driedfish.project_psychology_health_assist.rag.chunk;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown 切块：按二级标题（{@code ## }）切开，每个小节一块。
 *
 * <p>为什么按标题而不是按字数：标题本身就是语义边界，切开后每块只讲一个主题，
 * 向量才纯。固定滑窗会把两个不相关的小节拼在一起，检索时互相稀释。</p>
 *
 * <p>单块超长时再按段落二次切分，避免一个小节几千字把 topK 挤满。</p>
 */
@Component
public class MarkdownHeadingChunker implements DocumentChunker {

    /** 单块字符上限，超过就按段落再切。 */
    private static final int MAX_CHARS = 800;

    @Override
    public List<Document> split(String text, Map<String, Object> metadata) {
        String source = String.valueOf(metadata.getOrDefault("source", "unknown"));
        // 去掉文件头的 HTML 注释（source / license 等元信息）
        String cleaned = text.replaceAll("(?s)<!--.*?-->", "").trim();
        String title = extractTitle(cleaned, source);
        String body = cleaned.replaceFirst("(?m)^#\\s+.*(\\R|$)", "");

        List<Document> docs = new ArrayList<>();
        int index = 0;
        for (String section : body.split("(?m)^##\\s+")) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String heading = firstLine(trimmed);
            String content = trimmed.substring(heading.length()).trim();
            for (String piece : splitByLength(content)) {
                docs.add(new Document(
                        source + "#" + index,
                        "《" + title + "》" + heading + "\n" + piece,
                        chunkMetadata(metadata, source, title, heading)));
                index++;
            }
        }
        return docs;
    }

    private List<String> splitByLength(String content) {
        List<String> pieces = new ArrayList<>();
        if (content.length() <= MAX_CHARS) {
            pieces.add(content);
            return pieces;
        }
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\R{2,}")) {
            if (current.length() > 0 && current.length() + paragraph.length() > MAX_CHARS) {
                pieces.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(paragraph).append("\n\n");
        }
        if (current.length() > 0) {
            pieces.add(current.toString().trim());
        }
        return pieces;
    }

    private Map<String, Object> chunkMetadata(Map<String, Object> metadata, String source, String title, String heading) {
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("source", source);
        meta.put("title", title);
        meta.put("section", heading);
        return meta;
    }

    private String extractTitle(String text, String fallback) {
        var matcher = java.util.regex.Pattern.compile("(?m)^#\\s+(.+)$").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl).trim();
    }
}
