# 心理健康 AI 助手

一个面向真实业务约束的心理健康 AI 对话服务。项目以 Java 21 与 Spring Boot 3 为基础，在教程型实现之上完成安全、并发、数据访问与异常治理的架构级重构，重点验证 AI 应用在生产环境中的工程边界。

## 项目简介

本项目提供用户认证、心理咨询会话管理、历史消息查询与 SSE 流式对话能力。核心价值不在于简单接入大模型，而在于围绕 **身份可信、权限细粒度、阻塞隔离、连接可控、数据访问稳定** 建立完整的后端防线。

技术壁垒集中体现在：

- 以纵深防御模型治理 IDOR、水平越权与垂直越权风险。
- 以 Reactor 响应式流承载流式对话，同时隔离 JDBC 等阻塞调用。
- 以 Redis 黑名单、全局异常治理和高可用降级提升系统可运营性。
- 以聚合查询与局部更新控制数据库负载，避免 N+1 查询和数据置空。

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

## 核心技术栈

| 分类 | 技术 |
| --- | --- |
| 运行时 | Java 21、Spring Boot 3.4 |
| AI 编排 | Spring AI、OpenAI-compatible API |
| Web 层 | Spring MVC、Spring WebFlux、Reactor、SSE |
| 安全 | Spring Security、JWT Bearer Token、AOP 权限切面 |
| 数据层 | MySQL、MyBatis-Plus |
| 缓存与状态 | Redis、Token 黑名单 |
| 工程能力 | Jakarta Validation、Lombok、全局异常治理 |

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
- Redis 7+
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
export JWT_SECRET_KEY="a-long-random-secret-at-least-256-bits"
```

同步修改 `src/main/resources/application.yml` 中的 MySQL、Redis 连接信息。

### 3. 本地启动

```bash
./mvnw clean spring-boot:run
```

Windows PowerShell：

```powershell
.\mvnw.cmd clean spring-boot:run
```

服务默认监听 `8080` 端口。

### 4. Docker 依赖启动（可选）

```bash
docker run -d --name mental-health-mysql \
  -e MYSQL_ROOT_PASSWORD=change-me \
  -e MYSQL_DATABASE=mental_health_assist \
  -p 3306:3306 mysql:8.0

docker run -d --name mental-health-redis -p 6379:6379 redis:7-alpine
```

生产环境请使用密钥管理系统、非 root 数据库账号、TLS 与持久化卷，不要直接复用示例密码。

## 未来演进规划

- **RAG 知识库**：接入心理健康权威资料、向量数据库与引用溯源，增强回答的可验证性。
- **上下文持久化升级**：将内存态对话上下文替换为可扩展的 RedisChatMemory 持久化方案，并完善 TTL、分片与热数据治理。
- **可观测性**：引入 OpenTelemetry、TraceId、指标与结构化审计日志，建立从 SSE 请求到模型调用的全链路追踪。
- **可靠性建设**：增加限流、熔断、重试预算、模型降级与异步消息削峰机制。
- **隐私与合规**：完善敏感信息脱敏、数据生命周期管理、访问审计与用户数据删除能力。

## 免责声明

本项目用于技术研究与工程实践，不构成医疗诊断、治疗或危机干预建议。真实部署前应完成安全评审、隐私合规评估和专业心理服务流程建设。
