# RAG 语料来源与许可

> 本文件记录向量库中知识文档的来源、许可以及更新方式。
> **不要把出处不明的材料放进 `resources/data/rag/`。**

## 当前语料

| 文件 | 标题 | 来源 | 许可 |
|---|---|---|---|
| `data/rag/who/mental-health.md` | 精神卫生 | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/mental-health-strengthening-our-response) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/depression.md` | 抑郁障碍（抑郁症） | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/depression) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/anxiety.md` | 焦虑症 | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/anxiety-disorders) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/adolescent-mental-health.md` | 青少年精神卫生 | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/adolescent-mental-health) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/mental-disorders.md` | 精神障碍（总览） | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/mental-disorders) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/suicide.md` | 自杀 | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/suicide) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/bipolar-disorder.md` | 双相情感障碍 | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/bipolar-disorder) | CC BY-NC-SA 3.0 IGO |
| `data/rag/who/ptsd.md` | 创伤后应激障碍 | [WHO 实况报道](https://www.who.int/zh/news-room/fact-sheets/detail/post-traumatic-stress-disorder) | CC BY-NC-SA 3.0 IGO |
| `data/rag/cn/moe-college-mental-health-guideline.md` | 高等学校学生心理健康教育指导纲要 | [教育部（教党〔2018〕41号）](http://www.moe.gov.cn/srcsite/A12/moe_1407/s3020/201807/t20180713_342992.html) | 政府公开文件 |
| `data/rag/cn/mental-health-literacy-10.md` | 心理健康素养十条 | 国家卫生健康委员会疾控局（2018） | 政府公开科普材料 |
| `data/rag/cn/hotline-12356.md` | 全国统一心理援助热线 12356 | 国家卫生健康委员会通知（文本由项目维护者提供） | 政府公开文件 |
| `data/psychology_data.txt` | 心理问答测试语料 | 项目内测试数据 | 项目自有 |

WHO 内容按 **CC BY-NC-SA 3.0 IGO** 使用：非商业（本项目为公益项目）、署名、相同方式共享。
对外展示时需保留来源标注。

## 语料结构

- **知识层**（`data/rag/who/`、`data/rag/cn/`）：疾病科普、官方政策、求助渠道，按 `##` 小节切块，每块带《文档标题》前缀；
- **议题层**（`data/psychology_data.txt`）：问答式测试语料，按「问：」切块；
- 当前规模：11 篇文档 / 83 个小节 + 49 组问答，入库约 **132 块**。

## 建议补充（尚未抓取）

- [ ] 精神分裂症、自闭症、老年人精神健康（WHO 同样有中文实况报道，按需补）
- [ ] 高校心理中心的公开科普材料（各校差异大，选本校的）
- [ ] 睡眠、压力管理类自助手册（WHO《在压力下做重要的事情》为 PDF，需先转文本）

## 新增一篇文档的流程

1. 抓取原文，清洗成 Markdown：保留 `# 标题` 与 `## 小节`，删掉导航、评论、相关链接；
2. 文件头加 HTML 注释：`source` / `title` / `license` / `publisher`；
3. 放到 `src/main/resources/data/rag/<来源>/`；
4. 清一次旧向量（见下），重启应用自动入库。

## 清空向量库（换语料后必须做）

```bash
redis-cli --scan --pattern 'embedding:*' | xargs -r redis-cli del
redis-cli FT.DROPINDEX spring-ai-index DD
```

`spring-ai-index` 与 `embedding:` 是 `RedisVectorStore` 的默认命名。
