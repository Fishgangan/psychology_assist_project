# RAG 基线记录

> 目的：**每次改动都能对比，而不是“感觉变好了”。**
> 新增一次测试就往下追加一行，不要覆盖历史。

## 观测方法

在 `ChatClientConfig` 的 `chat` bean 上挂 `SimpleLoggerAdvisor(10)`（order 必须大于
`QuestionAnswerAdvisor` 的 0，否则看不到 RAG 注入后的内容），并开启：

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
```

一次请求可以拿到三样东西：

1. `qa_retrieved_documents` 里的 **id / score** —— 判断命中了哪篇的哪个小节；
2. 注入后的 prompt 全文 —— 看拼接格式和内容质量；
3. response 里的 **promptTokens** —— 上下文成本。

## 当前配置快照

| 项 | 值 |
|---|---|
| Embedding | SiliconFlow `BAAI/bge-m3`（1024 维） |
| 向量库 | Redis Stack，`RedisVectorStore`，索引 `spring-ai-index` |
| 切块 | WHO/教育部/卫健委按 `##` 标题切；`psychology_data.txt` 按「问：」切；id = `source#序号`（幂等） |
| 语料 | WHO 8 篇（58 节）+ 教育部纲要（8 节）+ 素养十条（11 节）+ 12356（5 节）+ 测试问答 49 组 ≈ **132 块** |
| 检索 | `QuestionAnswerAdvisor`：topK=3 |
| System | 角色提示 + 【历史对话摘要】（记忆装配层产出）；危机话术首选 12356 |

## 测试记录

### 用例 1 · 议题型（topK=4 时测）

**问题**：我是讨好型人格，不敢拒绝别人怎么办？

| 排名 | 命中 id | score |
|---|---|---|
| 1 | `psychology_data.txt#8` | 0.891 |
| 2 | `psychology_data.txt#15` | 0.797 |
| 3 | `psychology_data.txt#45` | 0.779 |
| 4 | `psychology_data.txt#20` | 0.772 |

- promptTokens：**1111**
- 结论：WHO 三篇**未命中**，因为语料里没有“讨好 / 边界感 / 拒绝”的知识点。
  命中测试问答是正确行为 —— 这是**语料覆盖问题，不是检索问题**。
- 附带验证：切块修复生效，4 条都是完整问答，开头不再是半句。

### 用例 2 · 知识型（topK=4 时测）

**问题**：抑郁症有哪些症状？

| 排名 | 命中 id | score |
|---|---|---|
| 1 | `depression.md#2`（症状和类型） | 0.858 |
| 2 | `depression.md#1`（概述） | 0.831 |
| 3 | `anxiety.md#2`（症状和模式） | 0.822 |
| 4 | `depression.md#3`（成因和预防） | 0.818 |

- promptTokens：**2185**
- 结论：**top1 完全正确**，说明语料 + 切块 + 检索都通。
- 问题：第 3、4 条属于噪声（问症状却召回“焦虑症类型”“成因预防”）；
  WHO 单块 600–800 字，4 条就把 prompt 撑到 2185 tokens。

### 用例 3 · 知识型（topK=3，同一会话第 3 轮）

**问题**：抑郁症有哪些症状？

| 排名 | 命中 id | score |
|---|---|---|
| 1 | `depression.md#2`（症状和类型） | 0.857 |
| 2 | `depression.md#1`（概述） | 0.831 |
| 3 | `anxiety.md#2`（症状和模式） | 0.822 |

- promptTokens：**2545**（比用例 2 的 2185 **更高**）
- 结论：检索正确，去掉 topK 第 4 条后噪声消失。
  但 promptTokens 反而上升 —— **不是 RAG 变多了，是历史消息变多了**：
  这一轮包含了前两轮完整问答（AI 回复各 600+ tokens）。
  由此得到一条测量原则：**promptTokens 只能在同轮次、同会话状态下比较**，
  否则会被记忆增长污染。要横向对比 RAG，请开新会话、问第一句。

### 用例 4 · 隐晦危机表达（topK=3，新会话第 1 轮）

**问题**：我觉得活着没什么意思

| 排名 | 命中 id | score |
|---|---|---|
| 1 | `psychology_data.txt#17`（人生毫无目标） | 0.794 |
| 2 | `psychology_data.txt#25`（社团杂活） | 0.778 |
| 3 | `psychology_data.txt#32`（觉得自己平庸） | 0.776 |

- promptTokens：**707**（新会话第一轮，没有历史 —— 再次印证历史才是 token 大头）
- 结论：**RAG 召回失败**。`suicide.md` 一条都没进来，检索到的是“低落/虚无”调性的普通问答。
  - 原因：向量匹配的是**语义相近**，不是**意图相近**。“活着没什么意思”离“人生毫无目标”更近，
    而 `suicide.md` 用的是“自杀/风险人群/预防”这类书面词，反而更远。
  - 但同一条消息的**其他链路都正确**：
    - 风险分级 `riskLevel=2`、`riskDescription=需要心理疏导`（枚举同源）；
    - 回复给出 12356 + 追问自伤念头 + 安全建议；
    - 情绪分析 label=危机、keywords 含“自杀意念”。
  - 结论：**危机兜底不依赖 RAG**，靠的是规则 + 系统提示词；RAG 只是知识补充。

### 用例 5~7 · 待补充

- 「焦虑症怎么治疗？」—— 应命中 `anxiety.md` 诊断和治疗
- 「心理治疗和吃药哪个先？」—— 跨篇推理，看能否召回两篇的治疗小节
- 「我最近总失眠怎么办？」—— 议题型 + 素养十条第 4 条，看谁胜出

## 观察与风险

1. **分数区间被压缩**：bge-m3 在中文短问句上普遍落在 0.77–0.89。
   曾试过 `similarityThreshold=0.80`，对口语化问题有“全被过滤”的风险，已去掉，只保留 topK。
2. **单条注入体积大**：WHO 一节 600–800 字，topK=3 约 2000 字进 prompt。
   若要继续压，优先减小切块（`MarkdownHeadingChunker.MAX_CHARS`），而不是砍 topK。
3. **语料分层已经见效**：知识型问题命中 WHO，议题型问题命中问答语料。
4. **promptTokens 的主要增长源是历史消息，不是 RAG**：
   AI 单条回复就有 600+ tokens，记忆窗口 30 条，长会话会持续膨胀
   （用例 1 → 用例 3，同一会话三轮：1111 → 2545）。
   下一步优化：装配时对历史消息做**字符/ token 截断**（例如 AI 回复超过 200 字截断），
   而不是只按条数控制；否则窗口长度够了，体积仍然爆炸。

5. **语义相近 ≠ 意图相近**（用例 4 的教训）：隐晦的危机表达在向量空间里更接近
   “低落/虚无”的普通问答，而不是专门的自杀文档。
   - 低成本对策：**document expansion** —— 在 `suicide.md` 里补一节「常见的求助表达」，
     把“活着没什么意思”“不想醒来”等口语写进文档（已加）；
   - 结构性对策：危机判定为 2 时**不走 RAG**，直接注入固定干预模板（留给 M7）。

## 复测流程

```bash
# 换语料或改切块后，先清库（否则旧向量还在）
docker exec -it redis-stack redis-cli FT.DROPINDEX spring-ai-index DD
docker exec -it redis-stack sh -c "redis-cli --scan --pattern 'embedding:*' | xargs -r redis-cli del"
```

1. 重启，日志确认 `RAG 入库完成：N 块`；
2. 依次问上面的用例，从 `qa_retrieved_documents` 抄 id 与 score；
3. 从 response 的 `usage.promptTokens` 抄成本；
4. 追加到本文件，写一句结论。

## 下一步

- **3b Query 改写**：口语化问题改写成检索式（先用基线判断是否真的需要）
- **3c 多路召回**：向量 + `FT.SEARCH`（BM25），RRF 融合
- **3d Rerank**：召回 10 条 → bge-reranker 重排 → 取 3
- **抽 `RagContextService`**：要插 rerank，就必须从 `QuestionAnswerAdvisor` 换成手动检索
  （和记忆层同构：接口 + 实现 + 可单测）
- **补测**：用例 5~7；以及“文档扩展”之后重测用例 4，看 `suicide.md` 能否被召回
