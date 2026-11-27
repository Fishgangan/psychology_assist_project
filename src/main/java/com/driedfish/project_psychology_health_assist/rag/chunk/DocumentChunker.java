package com.driedfish.project_psychology_health_assist.rag.chunk;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * 切块策略：把一份原始文档切成若干 {@link Document}。
 *
 * <p>抽成接口的原因：现在用的是结构化测试数据（问答对），以后会换成权威文档
 * （Markdown / PDF / 网页），切块规则完全不同。换数据源时只加一个实现，
 * 加载与入库流程不动。</p>
 *
 * <p>实现约定：<b>必须生成稳定的 id</b>（如 {@code source#序号}），
 * 这样重复灌库是覆盖而不是追加，否则每次重启都会翻倍。</p>
 */
public interface DocumentChunker {

    /**
     * @param text     文档全文
     * @param metadata 已确定的元数据（至少包含 source）
     * @return 切好的文档；id 由实现按 source + 序号生成
     */
    List<Document> split(String text, Map<String, Object> metadata);
}
