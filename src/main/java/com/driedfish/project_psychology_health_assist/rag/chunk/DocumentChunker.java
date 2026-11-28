package com.driedfish.project_psychology_health_assist.rag.chunk;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

// 切块策略：把一份原始文档切成若干 Document。
// 抽成接口是因为数据源会换（问答对 → Markdown / PDF / 网页），换时只加实现，加载与入库流程不动。
// 实现约定：必须生成稳定 id（source#序号），重复灌库才是覆盖而不是追加。
public interface DocumentChunker {

    // text 为文档全文，metadata 至少含 source；返回的 Document id 由实现按 source + 序号生成
    List<Document> split(String text, Map<String, Object> metadata);
}
