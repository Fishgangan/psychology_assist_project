package com.driedfish.project_psychology_health_assist.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

// RAG 检索：拿「改写后的查询词」去向量库取文档，拼成一段参考资料。
// 用改写词而不是用户原话：口语化问句和英文提问都能落到同一套中文语料上。
// 检索失败返回 null，由调用方跳过注入（对话不受影响）。
@Slf4j
@Service
public class RagContextService {

    private final VectorStore vectorStore;
    private final int topK;

    public RagContextService(VectorStore vectorStore,
                             @Value("${rag.top-k:3}") int topK) {
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    // 返回可直接拼进 prompt 的参考资料；没有命中或失败时返回 null
    public String buildContext(String userMessage, String searchQuery) {
        String query = buildQuery(userMessage, searchQuery);
        if (!StringUtils.hasText(query)) {
            return null;
        }
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK).build());
            if (docs == null || docs.isEmpty()) {
                return null;
            }
            StringBuilder context = new StringBuilder("参考资料：\n");
            for (Document doc : docs) {
                context.append("---\n").append(doc.getText()).append("\n");
            }
            context.append("\n请优先依据以上资料回答；资料里没有的，明确说明不知道，不要编造。");
            log.info("RAG 检索成功，查询词：{}", query);
            return context.toString();
        } catch (Exception e) {
            log.warn("RAG 检索失败，跳过注入：{}", query, e);
            return null;
        }
    }

    // 检索词 = 用户原话（截断）+ 模型改写的关键词。
    // 只留原话会带上口语噪声，只留改写词会丢掉具体说法（"睡不好"被改成"失眠"就偏离了语料用词）。
    private String buildQuery(String userMessage, String searchQuery) {
        String original = StringUtils.hasText(userMessage)
                ? StringUtils.truncate(userMessage.strip(), 100)
                : "";
        if (!StringUtils.hasText(searchQuery) || original.contains(searchQuery)) {
            return original;
        }
        return original.isEmpty() ? searchQuery : original + " " + searchQuery;
    }
}
