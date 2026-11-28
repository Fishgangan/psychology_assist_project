package com.driedfish.project_psychology_health_assist.rag.chunk;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Markdown 切块：按 ## 小节切开，每个小节一块。
// 按标题而不是按字数：标题就是语义边界，切开后每块只讲一个主题，向量才纯。
// 单块超长时按段落再切；段落本身超长时按句子切，避免出现几千字的巨块。
@Component
public class MarkdownHeadingChunker implements DocumentChunker {

    // 单块字符上限
    private static final int MAX_CHARS = 800;

    // 文件头的 HTML 注释（source / license），不参与向量化
    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    // 一级标题 "# 标题"：多行模式，且不会命中 "## 小节"
    private static final Pattern TITLE = Pattern.compile("(?m)^#\\s+(.+)$");

    // 一级标题整行，用于从正文里剔除
    private static final Pattern TITLE_LINE = Pattern.compile("(?m)^#\\s+.*(\\R|$)");

    // 二级标题，小节切分点
    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+");

    // 段落分隔：两个及以上换行
    private static final Pattern PARAGRAPH = Pattern.compile("\\R{2,}");

    // 句子断点：在句末标点之后切（标点留在前一句）
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？!?；;])");

    @Override
    public List<Document> split(String text, Map<String, Object> metadata) {
        String source = String.valueOf(metadata.getOrDefault("source", "unknown"));
        String cleaned = HTML_COMMENT.matcher(text).replaceAll("").trim();
        String title = extractTitle(cleaned, source);
        String body = TITLE_LINE.matcher(cleaned).replaceFirst("");

        List<Document> docs = new ArrayList<>();
        int index = 0;
        for (String section : SECTION.split(body)) {
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

    // 把小节压到 MAX_CHARS 内：先按段落装箱，段落本身超长再按句子拆
    private List<String> splitByLength(String content) {
        if (content.length() <= MAX_CHARS) {
            return List.of(content);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : PARAGRAPH.split(content)) {
            for (String unit : splitParagraph(paragraph)) {
                if (current.length() > 0 && current.length() + unit.length() > MAX_CHARS) {
                    pieces.add(current.toString().trim());
                    current.setLength(0);
                }
                current.append(unit).append("\n\n");
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString().trim());
        }
        return pieces;
    }

    // 段落超过上限时按句子切；单句仍超长才硬切
    private List<String> splitParagraph(String paragraph) {
        if (paragraph.length() <= MAX_CHARS) {
            return List.of(paragraph);
        }
        List<String> units = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(paragraph)) {
            String rest = sentence.trim();
            while (rest.length() > MAX_CHARS) {
                units.add(rest.substring(0, MAX_CHARS));
                rest = rest.substring(MAX_CHARS);
            }
            if (!rest.isEmpty()) {
                units.add(rest);
            }
        }
        return units;
    }

    private Map<String, Object> chunkMetadata(Map<String, Object> metadata, String source, String title, String heading) {
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("source", source);
        meta.put("title", title);
        meta.put("section", heading);
        return meta;
    }

    private String extractTitle(String text, String fallback) {
        var matcher = TITLE.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl).trim();
    }
}
