# 心理健康 AI 助手

一个面向真实业务约束的心理健康 AI 对话服务。项目以 Java 21 与 Spring Boot 3 为基础，在教程型实现之上完成安全、并发、数据访问与异常治理的架构级重构；并在此基础上实现了流式对话、长期记忆、情绪感知、风险识别与 RAG 知识检索，重点验证 AI 应用在生产环境中的工程边界。

## 项目简介

本项目提供用户认证、心理咨询会话管理、历史消息查询与 SSE 流式对话能力，并进一步构建了情绪分析与风险分级的完整链路。核心价值不在于简单接入大模型，而在于围绕 **身份可信、权限细粒度、阻塞隔离、连接可控、数据访问稳定、AI 链路可治理** 建立完整的后端防线。

技术壁垒集中体现在：

- 以纵深防御模型治理 IDOR、水平越权与垂直越权风险。
- 以 Reactor 响应式流承载流式对话，同时隔离 JDBC 等阻塞调用。
- 以 Redis 黑名单、全局异常治理和高可用降级提升系统可运营性。
- 以聚合查询与局部更新控制数据库负载，避免 N+1 查询和数据置空。
- 以多 ChatClient 通道 + Advisor 治理 AI 编排，隔离对话、情绪、总结三类调用。
- 以"规则 + 模型"融合实现情绪感知与风险分级，兼顾确定性与语义理解。

## 系统架构设计

### 1. 纵深防御：从认证到资源归属的双层鉴权

- **标准化身份认证**：采用 JWT Bearer Token 规范，通过 Spring Security 过滤链完成令牌解析、校验与上下文注入。
- **强制下线机制**：登出后将 Token 写入 Redis 黑名单，在有效期内拒绝再次访问。
- **细粒度权限控制**：`@CheckSessionOwner` + AOP 校验会话归属，阻断水平越权；`@RequireAdmin` + 管理员切面约束高权限操作，阻断垂直越权。
- **IDOR 防护**：所有会话、消息等资源访问均绑定当前登录用户身份，不信任客户端传入的用户标识。

### 2. 响应式隔离：可取消的 SSE 流式对话

- 使用 Reactor `Flux` 与 `text/event-stream` 实现增量式 AI 输出，降低首字节延迟并改善交互体验。
- 通过 `boundedElastic` 调度器隔离 JDBC 等阻塞操作，避免阻塞 Netty 事件循环。
- 监听 `SignalType.CANCEL` 处理客户端断连，及时终止下游计算、释放上下文与连接资源，降低资源浪费和内存泄漏风险。
- 对 AI 中途异常统一转化为 SSE `error` 事件，保证协议层可感知、客户端可恢复。

### 3. 数据访问优化：固定查询次数与局部更新

- 修复会话列表 N+1 查询：通过 `IN + GROUP BY` 批量聚合消息统计，将查询次数收敛为固定 2 次。
- 用户资料采用局部更新策略，仅更新请求中显式提供的字段，避免空值覆盖已有数据。
- 使用 MyBatis-Plus 统一实体、分页与条件构造，保持数据访问层解耦和可维护性。

### 4. 高可用治理：异常收敛与降级策略

- 全局异常拦截统一处理业务异常、参数校验异常及 JSON 解析格式错误，避免堆栈信息泄露。
- Redis 黑名单校验采用 Fail-open 降级策略：Redis 不可用时优先保障核心业务可用，并通过日志与监控暴露风险。
- 全面采用构造器注入，移除静态依赖与隐式状态，提升可测试性和组件生命周期可控性。

### 5. AI 编排：多通道 ChatClient 与 Advisor 治理

- 拆分为三个独立 ChatClient 通道，按职责隔离：
  - `chat`：主对话，挂载 `MessageChatMemoryAdvisor`（Redis 长期记忆）与请求级 `QuestionAnswerAdvisor`（RAG 检索）。
  - `emotionTag`：单条情绪标签，独立模型、无记忆 Advisor，避免污染对话记忆。
  - `emotionComplete`：会话级情绪总结（JSON），同样独立隔离。
- Prompt 统一收敛至 `PromptManage`，业务代码不散落魔法字符串。
- 大模型 JSON 输出采用防御性解析：剥离 markdown 围栏、截取 `{}` 边界、反序列化失败降级。

### 6. 情绪感知与风险分级

- **消息级**：每条用户消息经 `emotionTag` 通道生成情绪标签，写入 `message.emotion_tag`。
- **会话级**：聚合最近 N 条用户消息生成 11 字段情绪 JSON（icon / label / keywords / riskLevel / riskDescription / timestamp / isNegative / suggestion / emotionScore / primaryEmotion / improvementSuggestions），写入 `session.last_emotion_analysis`。
- **风险分级三方融合**：`max(关键词规则, 情绪标签映射, 大模型语义判断)`，负面情绪兜底为 `1`。
  - 关键词命中显性高危词（自杀/自残等）恒为 `2`，**大模型可升级风险、不可降级风险**。
  - 规则负责确定性与可审计，大模型负责补隐晦表达（如"不想撑了""想消失"）的盲区。
  - `riskLevel` 与 `riskDescription` 由 `RiskLevel` 枚举统一产出，**同源不漂移**（模型给的描述被丢弃）。

### 7. 长期记忆与 RAG

- **长期记忆**：Spring AI 1.0.3 未提供原生 Redis ChatMemory，项目手搓 `RedisChatMemory` 实现 `ChatMemory` 接口——基于 Redis List 存储、保留最近 N 条、设置 TTL，由记忆 Advisor 自动读写。
- **RAG**：Redis 向量库 + `QuestionAnswerAdvisor`，将知识库检索结果注入对话上下文。

## 核心技术栈

| 分类 | 技术 |
| --- | --- |
| 运行时 | Java 21、Spring Boot 3.4.1 |
| AI 编排 | Spring AI 1.0.3、OpenAI-compatible API（DashScope） |
| Web 层 | Spring MVC、Spring WebFlux、Reactor、SSE |
| 安全 | Spring Security、JWT Bearer Token、AOP 权限切面、Redis 黑名单 |
| 数据层 | MySQL 8、MyBatis-Plus |
| 缓存与状态 | Redis Stack（记忆 / 黑名单 / 向量库） |
| 向量与 Embedding | Redis VectorStore、SiliconFlow BAAI/bge-m3 |
| 工程能力 | Jakarta Validation、Lombok、全局异常治理、构造器注入 |
| 部署 | Docker（腾讯云轻量服务器，2核4G Ubuntu 24.04） |

## 功能进度

**已完成**

- [x] 用户认证授权（JWT + Redis 黑名单，登出/改密强制失效）
- [x] 水平越权（`@CheckSessionOwner`）、垂直越权（`@RequireAdmin`）、IDOR 防护
- [x] 全局异常处理与统一响应
- [x] 会话管理：创建、删除、分页列表（N+1 已修复，固定 2 次查询）
- [x] 消息管理：分页查询、保存，用户资料局部更新
- [x] SSE 流式对话（Flux + boundedElastic + 断连处理）
- [x] 手搓 RedisChatMemory 长期记忆
- [x] 消息级情绪标签（`message.emotion_tag`）
- [x] 会话级情绪分析 JSON（`session.last_emotion_analysis`）
- [x] 风险分级三方融合（关键词 + 情绪 + 大模型）
- [x] `riskLevel` / `riskDescription` 同源产出（`RiskLevel` 枚举）
- [x] RAG 基础检索（RedisVectorStore + QuestionAnswerAdvisor）

**规划中**

- [ ] Redis 记忆压缩/摘要（增量压缩、`SUMMARY|` 标记、摘要 + 近 K 条并存）
- [ ] 用户画像（静态 + 动态：情绪分布 / 主题 / 趋势）
- [ ] RAG 升级（Query 改写 → 多路召回 → Rerank）
- [ ] 上下文拼装（画像 + 情绪 + 记忆摘要 + RAG 分层注入提示词）
- [ ] 危机干预闭环（高危话术 + 求助热线 + 事件记录）
- [ ] 接口文档（SpringDoc / OpenAPI 3）

## 核心接口说明

除特别说明外，受保护接口均需携带：`Authorization: Bearer <JWT>`。

### 认证与用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/user/login` | 用户登录并签发 JWT |
| `POST` | `/api/user/add` | 用户注册 |
| `GET` | `/api/user/current` | 获取当前用户信息 |
| `POST` | `/api/user/update` | 更新当前用户资料 |
| `POST` | `/api/user/updatePassword` | 更新当前用户密码 |
| `POST` | `/api/user/logout` | 登出并写入 Redis 黑名单 |

### 心理对话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/psychological-chat/session/start` | 创建心理咨询会话 |
| `POST` | `/api/psychological-chat/stream` | SSE 流式生成 AI 回复；受 `@CheckSessionOwner` 保护 |
| `GET` | `/api/psychological-chat/sessions` | 分页查询当前用户会话列表 |
| `GET` | `/api/psychological-chat/session/{sessionId}/messages` | 分页查询会话消息；受归属校验保护 |
| `DELETE` | `/api/psychological-chat/sessions/{sessionId}` | 删除会话；受归属校验保护 |

### 权限拦截边界

- `@CheckSessionOwner`：在进入业务方法前校验 `sessionId` 与当前用户的资源归属。
- `@RequireAdmin`：在进入管理类业务方法前校验管理员身份。
- Spring Security Filter Chain：统一完成 JWT 解析、认证上下文建立与未授权请求拦截。

## 快速启动

### 前置依赖

- JDK 21+
- Maven 3.9+
- MySQL 8+
- Redis 7+（建议 Redis Stack，支持向量检索）
- 可用的 OpenAI-compatible 模型服务

### 1. 创建数据库

```sql
CREATE DATABASE mental_health_assist DEFAULT CHARACTER SET utf8mb4;
```

根据项目实体补充用户、会话和消息表结构，并为 `session_id`、`user_id` 等高频条件建立索引。

### 2. 配置环境变量

不要将真实密钥提交到仓库：

```bash
export DASHSCOPE_API_KEY="your-api-key"
export SILICONFLOW_API_KEY="your-api-key"
export JWT_SECRET_KEY="a-long-random-secret-at-least-256-bits"
export MYSQL_PASSWORD="your-db-password"
```

同步修改 `src/main/resources/application.yml` 中的 MySQL、Redis 连接信息。

### 3. 本地启动

```bash
mvn clean spring-boot:run
```

> 仓库当前未提供 Maven Wrapper（`mvnw`），需本机已安装 Maven 3.9+。

服务默认监听 `8080` 端口。

### 4. Docker 依赖启动（可选）

```bash
docker run -d --name mental-health-mysql \
  -e MYSQL_ROOT_PASSWORD=change-me \
  -e MYSQL_DATABASE=mental_health_assist \
  -p 3306:3306 mysql:8.0

docker run -d --name mental-health-redis -p 6379:6379 redis:7-alpine
```

生产环境请使用密钥管理系统、非 root 数据库账号、TLS 与持久化卷，不要直接复用示例密码。Redis 端口务必设置密码或通过安全组限制访问，避免裸奔暴露公网。

## 未来演进规划

- **记忆压缩**：每数十条对话增量压缩为一条摘要，配合"摘要 + 近 K 条原始"并存，防止上下文溢出。
- **用户画像**：从情绪标签与会话总结中累积静态/动态画像，反哺个性化检索与对话。
- **RAG 升级**：Query 改写、向量 + BM25 多路召回、Rerank 重排，并支持引用溯源。
- **上下文拼装**：将画像、情绪、记忆摘要、RAG 结果分层注入提示词，并做 token 预算控制。
- **危机干预闭环**：高危识别后切换干预话术、提供求助热线并记录危机事件。
- **可观测性**：引入 OpenTelemetry、TraceId、指标与结构化审计日志，建立从 SSE 请求到模型调用的全链路追踪。
- **可靠性建设**：增加限流、熔断、重试预算、模型降级与异步消息削峰机制。
- **隐私与合规**：完善敏感信息脱敏、数据生命周期管理、访问审计与用户数据删除能力。

## 免责声明

本项目用于技术研究与工程实践，不构成医疗诊断、治疗或危机干预建议。真实部署前应完成安全评审、隐私合规评估和专业心理服务流程建设。
