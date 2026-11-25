# 项目提纲：心理健康 AI 助手（project_psychology_health_assist）

> **本文档面向 AI 协作者与后续开发者。** 读完本文件即可掌握项目全貌、技术约定与开发路线，可直接据此继续编码，无需重新摸索。

---

## 0. 快速索引

| 主题 | 章节 |
|---|---|
| 项目是什么、定位 | §1 |
| 技术栈与版本 | §2 |
| 系统架构、安全防线与依赖方向 | §3 |
| 接口清单 | §4 |
| 代码目录与类职责 | §5 |
| 数据模型（表与字段） | §6 |
| 核心业务流程（对话/情绪/记忆） | §7 |
| 已完成功能 | §8 |
| 待办路线图 | §9 |
| 必须遵守的设计约定 | §10 |
| 已知问题 / TODO | §11 |
| 环境与部署 | §12 |
| 给 AI 的开发提示 | §13 |

---

## 1. 项目概览

- **项目名**：`project_psychology_health_assist`（心理健康 AI 助手）
- **定位**：纯公益、面向大学生的对话式心理支持服务。在教程型实现之上完成了**安全、并发、数据访问与异常治理的架构级重构**，重点验证 AI 应用在生产环境中的工程边界。
- **形态**：Java 后端 API 服务（Spring Boot），提供 SSE 流式对话；前端来自原作者项目（本仓库聚焦后端）。
- **核心价值**：不只是"接个大模型"，而是围绕 **身份可信、权限细粒度、阻塞隔离、连接可控、数据访问稳定** 建立完整后端防线。
- **核心能力**：
  1. **流式心理对话**（SSE / Reactor）
  2. **长期记忆**（Redis，按会话保留对话历史）
  3. **情绪分析**（消息级情绪标签 + 会话级情绪总结 JSON）
  4. **风险分级**（关键词规则 + 情绪标签 + 大模型三方融合）
  5. **RAG 知识检索**（Redis 向量库 + QuestionAnswerAdvisor）
  6. **用户画像**（规划中）
  7. **上下文拼装**（规划中，把 RAG/情绪/记忆/summary 组装进提示词）

- **项目来源**：在原作者 `mental_health_assistant` 数据库与功能基础上重构、扩展而来。

---

## 2. 技术栈（精确版本）

| 分类 | 选型 | 说明 |
|---|---|---|
| 运行时 | Java 21、Spring Boot 3.4.1 | |
| AI 编排 | Spring AI 1.0.3、OpenAI-compatible API | **注意：无原生 Redis ChatMemory，需手搓** |
| Web 层 | Spring MVC、Spring WebFlux、Reactor、SSE | |
| 安全 | Spring Security、JWT Bearer Token、AOP 权限切面 | |
| ORM | MyBatis-Plus | Lambda 条件构造器、分页 |
| 数据库 | MySQL 8.0（`mental_health_assist`） | 业务数据 |
| 缓存与状态 | Redis Stack 7.x（云端 `49.232.209.73:6379`） | 记忆、JWT 黑名单、向量库 |
| 工程能力 | Jakarta Validation、Lombok、全局异常治理 | |
| 大模型 | DashScope（阿里云百炼）OpenAI 兼容接口 | 见下 |
| 主对话模型 | `qwen4-max` | 暂用免费额度，后续可能切换 |
| 情绪模型 | `qwen3.8-flash` | 单条情绪标签 + 会话情绪总结 |
| Embedding | SiliconFlow `BAAI/bge-m3`（1024 维） | RAG 向量化 |
| 部署 | 腾讯云轻量应用服务器（2核4G，Ubuntu 24.04，Docker） | 公网 IP `49.232.209.73` |

---

## 3. 系统架构、安全防线与依赖方向

### 3.1 分层

```
Controller（REST / SSE）
        │
        ▼
Service（业务编排）
        ├── PsychologicalSupportService   对话编排 + SSE
        ├── EmotionAnalysisService        情绪标签 + 会话情绪总结
        ├── ConsultationSessionService    会话 CRUD
        └── ConsultationMessageService    消息 CRUD
        │
        ├── AI 通道（ChatClient）
        │     ├── chat             主对话（含记忆 advisor + RAG advisor）
        │     ├── emotionTag       单条情绪标签
        │     └── emotionComplete  会话情绪总结（JSON）
        │
        └── Mapper → MySQL
        │
Redis（记忆 / 黑名单 / 向量库）
```

### 3.2 安全防线（纵深防御）

1. **标准化身份认证**：JWT Bearer Token 规范；Spring Security Filter Chain 完成令牌解析、校验、认证上下文注入。
2. **强制下线机制**：登出后 Token 写入 Redis 黑名单，有效期内拒绝再次访问。
3. **水平越权防护**：`@CheckSessionOwner` + AOP 在进入业务方法前校验 `sessionId` 与会话归属。
4. **垂直越权防护**：`@RequireAdmin` + 管理员切面约束高权限操作。
5. **IDOR 防护**：会话、消息等资源访问一律绑定当前登录用户，不信任客户端传入的用户标识。
6. **异常不泄露**：全局异常拦截统一处理业务异常、参数校验异常、JSON 解析异常，避免堆栈外泄。
7. **降级策略**：Redis 黑名单校验采用 **Fail-open**（Redis 不可用时优先保核心业务可用，并通过日志/监控暴露风险）。

### 3.3 响应式与连接可控

- Reactor `Flux` + `text/event-stream` 增量输出，降低首字延迟。
- `boundedElastic` 调度器隔离 JDBC 等阻塞操作，避免阻塞 Netty 事件循环。
- 监听 `SignalType.CANCEL` 处理客户端断连，及时终止下游计算、释放资源。
- AI 中途异常统一转为 SSE `error` 事件，协议层可感知、客户端可恢复。

### 3.4 数据访问优化

- 会话列表 N+1 修复：`IN + GROUP BY` 批量聚合消息统计，查询次数收敛为固定 2 次。
- 用户资料局部更新：仅更新请求中显式提供的字段，避免空值覆盖。
- MyBatis-Plus 统一实体、分页与条件构造。

### 3.5 依赖方向（**必须保持单向，禁止循环**）

```
PsychologicalSupportService
        └──→ EmotionAnalysisService
                 ├──→ ConsultationMessageService
                 ├──→ ConsultationSessionService
                 ├──→ RiskAssessor
                 ├──→ ChatClient(emotionTag / emotionComplete)
                 └──→ ObjectMapper

ConsultationSessionService  ──→  Mapper / 独立，不反向依赖上层
ConsultationMessageService  ──→  Mapper / 独立，不反向依赖上层
```

- Service 之间**不允许 A↔B 互相注入**。
- 遇到"需要互相调用"时：抽出第三个公共 Service，或让调用单向。

### 3.6 三大 ChatClient 通道

| Bean 名 | 用途 | 关键配置 |
|---|---|---|
| `chat` | 主对话 | `defaultSystem`（心理支持提示词）+ `MessageChatMemoryAdvisor`（Redis 记忆）+ 请求级 `QuestionAnswerAdvisor`（RAG） |
| `emotionTag` | 单条情绪标签 | `defaultOptions(model=qwen3.8-flash)`，**无记忆 advisor**（避免污染） |
| `emotionComplete` | 会话情绪总结 JSON | 同上，独立 client |

> **为什么情绪分析必须用独立 client**：`MessageChatMemoryAdvisor` 会在每次调用前后自动读写 Redis 记忆。若复用主 `chat` client，情绪分析的提示词与结果会被写进记忆库，造成污染。

---

## 4. 接口清单

除特别说明外，受保护接口均需 `Authorization: Bearer <JWT>`。

### 4.1 认证与用户

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/user/login` | 登录并签发 JWT |
| `POST` | `/api/user/add` | 用户注册 |
| `GET` | `/api/user/current` | 获取当前用户信息 |
| `POST` | `/api/user/update` | 更新当前用户资料（局部更新） |
| `POST` | `/api/user/updatePassword` | 更新密码（旧 token 失效） |
| `POST` | `/api/user/logout` | 登出并写入 Redis 黑名单 |

### 4.2 心理对话

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/psychological-chat/session/start` | 创建心理咨询会话 |
| `POST` | `/api/psychological-chat/stream` | SSE 流式生成 AI 回复；受 `@CheckSessionOwner` 保护 |
| `GET` | `/api/psychological-chat/sessions` | 分页查询当前用户会话列表 |
| `GET` | `/api/psychological-chat/session/{sessionId}/messages` | 分页查询会话消息；受归属校验保护 |
| `DELETE` | `/api/psychological-chat/sessions/{sessionId}` | 删除会话；受归属校验保护 |

### 4.3 权限拦截边界

- `@CheckSessionOwner`：业务方法前校验 `sessionId` 与当前用户资源归属（水平越权）。
- `@RequireAdmin`：管理类方法前校验管理员身份（垂直越权）。
- Spring Security Filter Chain：统一完成 JWT 解析、认证上下文建立、未授权请求拦截。

---

## 5. 代码结构

> 包根：`com.driedfish.project_psychology_health_assist`

```
src/main/java/com/driedfish/project_psychology_health_assist/
├── AIService/
│   ├── PromptManage.java                # 所有 Prompt 常量（系统提示词 / 单条情绪 / 会话情绪总结）
│   ├── PsychologicalSupportService.java # 对话编排：startSession / streamPsychologicalChat / deleteSession
│   ├── RiskAssessor.java                # 风险规则引擎：关键词扫描 + 情绪标签映射
│   ├── redisChatMemory.java             # 手搓 Redis ChatMemory（实现 ChatMemory 接口）
│   └── StructOutPut.java                # 结构化输出 DTO：StreamChatSession / StreamChatSessionItem / StreamChatSessionList / StreamChatMessageList
├── annotation/
│   ├── CheckSessionOwner.java           # 水平越权校验注解
│   └── RequireAdmin.java                # 垂直越权校验注解
├── aspect/
│   ├── SessionOwnerAspect.java          # 会话归属校验切面
│   └── AdminRequireAspect.java          # 管理员校验切面
├── config/
│   ├── ChatClientConfig.java            # 定义 chat / emotionTag / emotionComplete 三个 ChatClient
│   ├── JwtConfig.java                   # JWT 密钥等配置
│   ├── MybatisPlusConfig.java           # MyBatis-Plus 配置
│   └── SecurityConfig.java              # Spring Security 过滤链
├── controller/
│   ├── PsychologicalChatController.java # 心理对话接口（含 SSE）
│   └── UserController.java              # 认证与用户接口
├── rag/
│   ├── RagDataInitializer.java          # 知识库数据初始化入库
│   └── config/RagConfig.java            # JedisPooled + RedisVectorStore 手动装配
├── service/
│   ├── ConsultationSessionService.java  # 会话 CRUD、越权校验、情绪分析落库、会话分页
│   ├── ConsultationMessageService.java  # 消息 CRUD、情绪标签落库、最近 N 条查询
│   ├── EmotionAnalysisService.java      # 情绪标签 + 会话情绪总结（核心）
│   ├── UserService.java                 # 注册 / 登录 / 资料 / 改密 / 登出
│   └── convert/                         # MessageConvert / UserConvert（DTO ↔ 实体）
├── common/
│   ├── DTO/
│   │   ├── internal/EmotionResult.java          # 情绪 JSON 11 字段（另有 TokenVerifyResult）
│   │   ├── command/                             # 各 CommandDTO + ConsultationSessionCreateDTO / ConsultationStreamDTO
│   │   └── response/                            # ConsultationMessageResponseDTO / SessionMessageCountDTO / UserDetailDTO / UserLoginResponseDTO
│   ├── entity/                          # User / ConsultationSession / ConsultationMessage
│   ├── exception/BusinessException.java
│   ├── GlobalExceptionHandler.java
│   ├── Result.java
│   └── ResultCode.java                  # 错误码枚举
├── enumClass/
│   ├── RiskLevel.java                   # 风险等级 0/1/2 + 文案（riskLevel 与 riskDescription 同源来源）
│   ├── UserStatus.java
│   └── UserType.java
├── mapper/                              # UserMapper / ConsultationSessionMapper / ConsultationMessageMapper
└── util/
    ├── JwtAuthenticationFilter.java     # JWT 解析与认证上下文注入
    ├── JwtTokenUtil.java                # 签发 / 校验
    ├── ResponseUtil.java
    ├── SecurityUtils.java               # 当前登录用户
    └── SessionUtils.java                # sessionId 与 dbSessionId 互转
```

### 5.1 关键类职责

- **`PsychologicalSupportService`**：对话主链路。`startSession` 建会话+存初始消息；`streamPsychologicalChat` 走 Reactor 流式；流结束后异步触发情绪分析（调 `EmotionAnalysisService`）。
- **`EmotionAnalysisService`**：`updateMessageEmotionTag`（单条标签）+ `updateSessionEmotionAnalysis`（会话总结 JSON）+ 私有 `buildEmotionAnalysisPrompt` / `parseEmotionJson`。
- **`RiskAssessor`**：`assess(message)` 关键词扫描；`assessByEmotion(tag)` 情绪映射。纯规则，可单测。
- **`RiskLevel`**：风险等级枚举。`fromCode` 对越界值收敛（>2 按最高等级、<0 按最低），并同时给出 `code` 与 `description`，是 `riskLevel` / `riskDescription` 的**唯一来源**。
- **`RedisChatMemory`**：`add` 写 Redis List（rightPush + trim 保留最近 N 条 + TTL），`get` 读取并反序列化。

---

## 6. 数据模型

### 6.1 核心表

#### `user`
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| username / email / phone | varchar | 唯一 |
| password | varchar | BCrypt |
| nickname / avatar | varchar | |
| gender / birthday | tinyint / date | 画像静态来源 |
| user_type | tinyint | 1-普通 2-管理员 |
| status | tinyint | 0-禁用 1-正常 |

#### `consultation_session`
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| user_id | bigint | 外键 → user |
| session_title | varchar(200) | 会话标题 |
| started_at | datetime | |
| **last_emotion_analysis** | **json** | 会话级情绪分析（11 字段 JSON） |
| **last_emotion_updated_at** | datetime | 情绪分析更新时间 |

#### `consultation_message`
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| session_id | bigint | 外键 → consultation_session |
| sender_type | tinyint | 1-用户 2-AI |
| message_type | tinyint | 1-文本 |
| content | text | 消息内容 |
| **emotion_tag** | varchar(50) | 单条情绪标签 |
| ai_model | varchar(50) | 生成该 AI 消息的模型 |
| created_at | datetime | |

### 6.2 遗留/待用表（原作者已有）
- `emotion_diary`（情绪日记，含 `ai_emotion_analysis` JSON）—— 暂不做，画像阶段可作为数据源
- `knowledge_article` / `knowledge_category`（知识库）—— RAG 数据源
- `user_favorite`（收藏）、`ai_analysis_task`（异步任务）、`sys_file_info`（文件）

### 6.3 会话情绪 JSON Schema（`EmotionResult`，11 字段）

```json
{
  "icon": "😔",
  "label": "低落",
  "keywords": ["心情", "不好"],
  "riskLevel": 0,
  "riskDescription": "情绪稳定",
  "timestamp": 1795352476088,
  "isNegative": true,
  "suggestion": "抱抱你，不开心的时候可以找朋友聊聊",
  "emotionScore": 45,
  "primaryEmotion": "悲伤",
  "improvementSuggestions": ["深呼吸放松", "听首喜欢的歌"]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `icon` | string | 表情 emoji |
| `label` | string | 简短情绪标签（2–4 字） |
| `keywords` | string[] | 与情绪相关的关键词 |
| `riskLevel` | int | 0 情绪稳定 / 1 需要关注 / 2 需要心理疏导 |
| `riskDescription` | string | 风险描述；由 `RiskLevel` 枚举按最终等级产出，与 `riskLevel` 同源 |
| `timestamp` | long | 毫秒时间戳，代码填充 |
| `isNegative` | boolean | 是否负面情绪 |
| `suggestion` | string | 一句安抚建议 |
| `emotionScore` | int | 0–100，越高越正面 |
| `primaryEmotion` | string | 主要情绪 |
| `improvementSuggestions` | string[] | 改善建议 2–4 条 |

> 该 schema **对齐原作者 `last_emotion_analysis` 格式**，不可随意改名，否则与前端/历史数据割裂。

---

## 7. 核心业务流程

### 7.1 会话创建 `startSession`
```
createSession(userId, dto)  → 落 session 表
saveUserMessage(sessionId, 初始消息) → 落 message 表（sender_type=1）
返回 sessionId = "session_"+id + 会话元信息
```

### 7.2 流式对话 `streamPsychologicalChat`
```
1. Mono.fromCallable（boundedElastic）：
   - 越权校验 checkSessionOwner
   - 判断是否初始消息（messageCount==1 且内容匹配 → 复用 startSession 已存的 id）
   - 否则 saveUserMessage 并拿到 userMessageId
   - return userMessageId
2. .flatMapMany(userMessageId -> chatClient.prompt()
       .user(userMessage)
       .advisors(param(CONVERSATION_ID) + questionAnswerAdvisor)
       .stream().content())
3. doOnNext 累积片段 → doOnComplete 异步（boundedElastic）：
   - saveAiMessage
   - emotionAnalysisService.updateMessageEmotionTag(userMessageId, userMessage)
   - emotionAnalysisService.updateSessionEmotionAnalysis(sessionId, userMessage, emotionTag)
4. doOnError（IOException=断连）/ doFinally（CANCEL / ON_COMPLETE）
```

> **核心约定**：情绪标签必须打在**用户消息**上（`userMessageId`），不得用 `aiMessageId-1` 推算。

### 7.3 单条情绪标签 `updateMessageEmotionTag`
```
emotionTagClient.prompt().user(EMOTION_ANALYSIS_PROMPT + "\n" + userMessage)
  → content() → trim → saveEmotionTag(messageId, tag) → return tag
异常内部 catch → 返回 null（不影响主对话）
```

### 7.4 会话情绪总结 `updateSessionEmotionAnalysis`
```
1. getRecentMessages(sessionId, 10)   # 仅用户消息、按 id 倒序取 N 条后 reverse
2. buildEmotionAnalysisPrompt(recent) # 逐条拼 "[情绪标签] 内容"，内容截断 60 字
3. emotionCompleteClient.call().content()
4. parseEmotionJson(content)          # 剥离 ```json 围栏 → 截取 {} → readValue → 失败返回 null
5. riskLevel 三方融合（见 7.5）
6. setRiskLevel / setRiskDescription / setTimestamp(now)
7. addLastEmotionAnalysis(sessionId, ObjectMapper.writeValueAsString(analysis))
```

### 7.5 风险分级（三方融合，取最大值）

```
keywordRisk = RiskAssessor.assess(userMessage)            # 关键词：高危→2 中危→1
emotionRisk = max(assessByEmotion(emotionTag),
                  assessByEmotion(primaryEmotion))        # 情绪标签：高危情绪→2 负面→1
modelRisk   = analysis.getRiskLevel()                     # 大模型语义判断（补隐晦表达盲区）

finalRisk = max(keywordRisk, emotionRisk, modelRisk)
if (isNegative && finalRisk == 0) finalRisk = 1            # 负面情绪兜底

RiskLevel level = RiskLevel.fromCode(finalRisk)            # 越界值在此收敛为 0/1/2
analysis.setRiskLevel(level.getCode())
analysis.setRiskDescription(level.getDescription())        # 必须与 riskLevel 同源
```

**核心原则：大模型可以"升级"风险，但绝不能"降级"风险。** 关键词命中高危词 → 无论模型怎么说都是 2。

### 7.6 记忆（`RedisChatMemory`）
- 基于 Redis List，键前缀 `chat:memory:`，会话 id 区分。
- 写入：`rightPush` + `trim` 保留最近 N 条 + 设置 TTL。
- 读取：`range` 解析后转为 Spring AI `Message` 列表。
- 由主 `chat` client 的 `MessageChatMemoryAdvisor` 自动调用（业务代码不手动 add）。

---

## 8. 已完成功能（✅）

1. 用户认证授权：JWT 签发 / 校验 + Redis 黑名单（登出、改密强制失效）
2. 越权防护：水平越权（`@CheckSessionOwner`）、垂直越权（`@RequireAdmin`）、IDOR 防护
3. 全局异常处理 + 统一响应结构
4. 会话管理：创建、删除、分页列表（**N+1 已修复：`IN + GROUP BY`，固定 2 次查询**）
5. 消息管理：分页查询、保存；用户资料**局部更新**（避免空值覆盖）
6. 流式对话：SSE + Flux + boundedElastic；`SignalType.CANCEL` 断连处理；AI 异常转 SSE error
7. 手搓 `RedisChatMemory`（Spring AI 1.0.3 无原生 Redis 记忆）
8. 消息级情绪标签（`message.emotion_tag`）
9. 会话级情绪分析 JSON（`session.last_emotion_analysis`，11 字段）
10. 风险分级：关键词 + 情绪标签 + 大模型三方融合
11. RAG 基础：`RedisVectorStore` + `QuestionAnswerAdvisor`
12. 结构化输出 DTO 拆分：`StreamChatSession` / `StreamChatSessionItem`
13. 多 ChatClient 装配：`chat` / `emotionTag` / `emotionComplete`
14. `riskLevel` 与 `riskDescription` 同源：统一由 `RiskLevel` 枚举产出（不再使用模型原值）
15. 记忆分层：`ConversationMemoryStore`（双 key）+ `ChatContextAssembler`（摘要进 system）+ `MemoryCompactor`（增量压缩）+ `ConversationMemoryService`（门面）；摘要双写 MySQL 真源，Redis 缺失时回落
16. 记忆接线：`chat` 通道移除 advisor 与 defaultSystem，链路改为 `prepare → messages → record → compact`，全部跑在 `boundedElastic` 上

---

## 9. 待办路线图（⏳，按顺序）

### 阶段 1：Redis 记忆总结 / 压缩（✅ 已完成）
- 实现落在 `AIService/memory`：`Store`（双 key 存取）、`Assembler`（快照 → messages）、`Compactor`（增量压缩）、`Facade`（prepare / record / compactIfNeeded / clear）
- 触发：`memory.compact.threshold`（默认 30 条消息）；压完保留 `memory.compact.keep`（默认 10 条）
- **增量压缩**：旧摘要 + 最旧一段对话 → 新摘要，早期信息不丢
- **双写**：MySQL `consultation_session.memory_summary` 为真源，Redis `chat_summary:` 为快路径；Redis 缺失时回落 MySQL 并回填
- **降级**：压缩失败不删原始消息；写入顺序 MySQL → Redis → 裁消息
- 待补：压缩防重入锁（见 §11）

### 阶段 2：用户画像提取
- **静态画像**：注册信息（性别/生日/职业等）
- **动态画像**：从 `emotion_tag` + 会话情绪 JSON 累积（情绪分布、高频主题、趋势）
- 存储：MySQL `user_profile` 表；用途：RAG 个性化过滤 + prompt 注入画像摘要

### 阶段 3：RAG 升级（👈 下一步）

现状：`RagConfig` 定义 `VectorStore`（`RedisVectorStore`，`initializeSchema=true`）与 `QuestionAnswerAdvisor`；
`RagDataInitializer` 灌 `resources/data/psychology_data.txt`；检索结果由 advisor 自动注入 prompt。

**3a 基线摸底（先做，不写新功能）**
- 准备 10 个典型问题（口语化 / 知识型 / 情绪型各几条），记录当前 topK 实际返回了什么
- 记录三个数：命中率、首字延迟、注入后的 token 长度
- 产出一份对比基线，否则后面改动无法判断好坏

**3b Query 改写**
- 新增 `queryRewrite` 通道（`qwen3.8-flash`），把口语化问题改写成检索式
- **只改写检索用的 query，不改给模型的原始用户消息**
- 验收：基线里"答非所问"的问题，至少一半改善

**3c 多路召回（向量 + BM25）**
- 向量：现有 `similaritySearch`
- 关键词：Redis Stack `FT.SEARCH`，两路结果用 RRF 融合
- 验收：专有名词类问题（如"认知行为疗法"）召回明显变好

**3d Rerank**
- SiliconFlow `bge-reranker` 对融合后的候选重排，取 topN
- 验收：注入的 top3 人工抽查相关性明显提升

**3e 引用溯源**
- 检索结果带上 `doc_id` / `title` / `score`，随回复返回或落日志
- 为阶段 4 的"分层拼装"提供结构化输入

**注意**：
- 3b–3d 都会增加首字延迟，必须跑在 `boundedElastic` 上，并设超时与降级（检索失败 → 不注入，对话照常）
- RAG 目前是 advisor；阶段 4 收编成手动拼装时再改

### 阶段 4：上下文拼装（收口）
- 抽独立 `ContextAssembler`，把以下拼进 prompt（分层）：
  1. System 角色（当前由记忆装配层产出）
  2. 用户画像摘要
  3. 当前情绪状态（情绪 JSON）
  4. 记忆摘要（summary）
  5. RAG 检索结果
  6. 最近对话（当前由 `prepare` 装配）
- **注意 token 预算**，按需注入，避免上下文溢出

### 阶段 5：接口文档
- SpringDoc OpenAPI（`springdoc-openapi-starter-webmvc-ui`）自动生成 Swagger UI

### 阶段 6：可观测性与可靠性（参考 README 规划）
- 可观测性：OpenTelemetry、TraceId、指标、结构化审计日志，SSE→模型调用全链路追踪
- 可靠性：限流、熔断、重试预算、模型降级、异步削峰

### 阶段 7：隐私与合规（参考 README 规划）
- 敏感信息脱敏、数据生命周期管理、访问审计、用户数据删除

### 建议插入：危机干预闭环（与阶段 1/2 并列）
- 高风险（riskLevel=2）→ 切换危机干预话术 + 提供求助热线（如全国心理援助热线 12356）
- 建 `crisis_event` 表记录高危事件（审计 + 画像素材）

---

## 10. 必须遵守的设计约定

### 10.1 业务规则
1. `riskLevel` 语义固定：`0` 情绪稳定 / `1` 需要关注 / `2` 需要心理疏导。
2. **风险取规则 + 模型最大值**，大模型只能升级不能降级。
3. `riskLevel` 与 `riskDescription` **必须同源生成**（单一事实来源，实现为 `RiskLevel` 枚举）。
4. 情绪分析数据源用 **MySQL 的 message 表**（稳定，不被压缩），不依赖 Redis 记忆。
5. 会话情绪 JSON 沿用原作者 11 字段格式，字段名不可随意改。
6. 情绪只从**用户消息**提取，AI 回复不参与。

### 10.2 技术规范
1. 依赖注入用**构造器注入 + final 字段**，`@Qualifier` 放构造器参数上。
2. 异常降级写在**方法内部**（catch + log + 返回/静默），调用方不包 try-catch。
3. Service 间**单向依赖**，禁止循环；Bean 装配只在 `@Configuration`。
4. Prompt 统一放 `PromptManage`，不在业务代码里写魔法字符串。
5. 大模型 JSON 输出必须走**防御性解析**（剥离围栏 → 截取 `{}` → readValue → 失败降级）。
6. 情绪标签写入前 `trim()`；只取用户消息、内容截断再喂模型。
7. 会话列表等聚合查询控制为**固定次数**，避免 N+1。
8. 风险等级与风险描述的映射统一放 `RiskLevel` 枚举，**不在业务代码里手写 switch / Map**；新增等级只改枚举一处。

### 10.3 记忆层约定（M3 定稿）

1. **三层边界**：`ConversationMemoryStore` 只做 Redis 存取（不含业务判断）、`ChatContextAssembler` 只做「快照 → `List<Message>`」（纯函数，可单测）、`ConversationMemoryService` 只做编排（prepare / record / compactIfNeeded）。**改任一层不得牵动另两层**。
2. **双 key、生命周期分离**：消息 `chat_memory:<id>`（LIST，TTL 7 天，`LTRIM` 保留最近 30 条）；摘要 `chat_summary:<id>`（STRING，TTL 30 天，覆盖写）。摘要**不进消息列表**——`LTRIM` 不支持“跳过某一条”，放同一列表里摘要必然被裁。代价是两个 key 可能短暂不一致，但摘要晚一轮更新不影响正确性。
3. **摘要注入走 system 文本**：由 `ChatContextAssembler.composeSystemPrompt` 拼成「角色提示 + 【历史对话摘要】+ summary」，整个 prompt 只有**一条** system 消息，不新增 `SystemMessage`。无摘要时返回值必须与 base 逐字一致（不能多个空段落）。接线后 `ChatClientConfig` 不再设 `defaultSystem`——system 的唯一来源是装配层。
4. **写入语义**（判断口诀：**能自愈的不用事务，会丢数据的才用事务**）：
   - `append` 不包 MULTI：`rightPushAll` / `trim` / `expire` 三条互不依赖，失败可自愈（多出的消息下次 trim、TTL 下次补）；
   - 压缩**必须先 `saveSummary` 再 `trimTo`**：反了会“消息已删、摘要未写”，那段对话永久丢失；先写摘要最坏只是重复描述；
   - 压缩失败不删消息、不覆盖旧摘要。
5. **旧 `ChatMemory` 退役但不删**：`redisChatMemory` 保留为回滚路径，新链路验证通过后再移除。它只认 `USER` / `ASSISTANT`，新链路写 `AI` 会解析失败，所以**回滚前先清 `chat_memory:*`**——记忆是缓存，MySQL 才是真相。

---

## 11. 已知问题 / TODO

- [x] ~~`riskDescription` 与 `riskLevel` 一致性~~：已改为 `RiskLevel.fromCode(...)` 统一产出 `code` + `description`，模型原值被丢弃。
- [x] ~~README "未来演进" 把已落地的 RAG、RedisChatMemory 标为未来~~：README 已更新。
- [ ] 情绪模型暂用 `qwen3.8-flash`，后续评估切换。
- [ ] `application.yml` 中 Redis 无密码（`spring.data.redis` 与 `vectorstore.redis.uri`），需补密码或安全组限制。
- [ ] `logging.level.ConsultationMessageMapper: debug` 仅用于本地调试，上线前需关闭。
- [ ] 情绪分析为流结束后异步执行，客户端断连（CANCEL）时不触发，属预期行为。
- [ ] 仓库未提供 Maven Wrapper（`mvnw` / `mvnw.cmd`），需本机安装 Maven 3.9+ 后执行 `mvn clean spring-boot:run`，或后续补 wrapper。
- [x] ~~记忆层接线~~：已完成——`chat` 去掉 advisor 与 `defaultSystem`；`prepare` 移到归属校验之后并在 `boundedElastic` 上执行；流结束后在同一个 Mono 内完成 record → AI 落库 → 情绪分析 → 压缩。
- [x] ~~摘要双写 MySQL~~：已完成——压缩时写顺序为 MySQL（真源，失败即停）→ Redis → 裁消息；`prepare` 时 Redis 摘要为空则回落 MySQL 并回填。
- [ ] **压缩防重入锁（必做）**：当前 `compactIfNeeded` 无锁，用户连发两条时两个请求可能同时压缩，后写的摘要覆盖先写的。单机用本地 `Set`（`ConcurrentHashMap.newKeySet()` + `finally` 移除）即可；多实例部署再换 Redis `SET NX EX`。
- [ ] 压缩 Prompt 待调：实测摘要里出现"新增对话中"这类元描述，且长度 549 字超过 300 字约束；需在 `MEMORY_COMPACT_PROMPT` 中加"不要提及摘要 / 对话 / 输入等元信息"，并收紧字数。
- [ ] RAG 启动期强依赖 embedding key：`initializeSchema(true)` 需要向量维度，会真的调一次 embedding 接口，key 无效会直接导致**启动失败**（已踩过一次）；考虑改成懒初始化或失败降级。

---

## 12. 环境与部署

### 12.1 前置依赖
- JDK 21+、Maven 3.9+
- MySQL 8+、Redis 7+（建议 Redis Stack，支持向量检索）
- 可用的 OpenAI-compatible 模型服务

### 12.2 数据库
```sql
CREATE DATABASE mental_health_assist DEFAULT CHARACTER SET utf8mb4;
```
按实体补充用户、会话、消息表结构，并为 `session_id`、`user_id` 等高频条件建索引。

### 12.3 环境变量（不要提交真实密钥）
```bash
export DASHSCOPE_API_KEY="your-api-key"
export SILICONFLOW_API_KEY="your-api-key"
export JWT_SECRET_KEY="a-long-random-secret-at-least-256-bits"
export MYSQL_PASSWORD="your-db-password"
```

### 12.4 本地启动
```bash
mvn clean spring-boot:run      # 需本机已安装 Maven 3.9+（仓库暂无 mvnw 包装器）
```
服务默认 `8080` 端口。

### 12.5 云端
- 腾讯云轻量服务器 `49.232.209.73`，Ubuntu 24.04，Docker。
- Redis 端口 6379（业务/记忆/向量），RedisInsight 8001。
- **安全要求**：Redis 6379 不可无密码暴露公网（历史曾因裸奔被扫描导致服务器强制关机）。

---

## 13. 给 AI 的开发提示（关键注意点）

1. **改情绪相关代码前**，先确认改的是 `EmotionAnalysisService`（单条/总结）还是 `RiskAssessor`（规则），别混。
2. **动 `EmotionResult` 字段时**，必须同步改 `PromptManage.EMOTION_COMPLETE_ANALYSIS_PROMPT`，否则模型输出与 DTO 对不上（Jackson 会静默丢字段，不报错，最容易踩）。
3. **新增 ChatClient 时**，用注入 `ChatClient.Builder`（prototype），不要 `ChatClient.builder(chatModel)` 手动构建（会丢默认 options / MessageConverter / Observation）。
4. **情绪分析类调用不要复用主 `chat` client**，否则污染 Redis 记忆。
5. **Reactor 里给外部变量赋值前**，先想清楚：变量必须在 lambda 内部声明或 `return` 出去，否则编译报 "effectively final"。
6. **`@Value` 字段在构造器里是 null**，需要构造器参数注入。
7. 依赖方向一旦发现循环，**重构**而不是 `@Lazy`。
8. 改风险等级文案时只动 `RiskLevel` 枚举，业务代码里不要再写第二份映射。
