# Nexus Agent 脚手架

本仓库提供一个基于 Java 的多智能体项目脚手架，后端使用 **Google ADK**，前端使用 **Vue3**。

## 技术架构

### 后端 (`backend/`)
- 语言/运行时: Java 17
- 框架: Spring Boot + Google ADK (`com.google.adk:google-adk:0.5.0`) + JDBC
- 职责: 智能体编排、技能加载与 API 暴露

支持的智能体拓扑:
- `SINGLE`: 单个 `LlmAgent` 处理完整请求
- `MASTER_SUB`: 由主 `LlmAgent` 委派给子智能体
- `MULTI_WORKFLOW`: 通过 `ParallelAgent` + `SequentialAgent` 管道进行分阶段协作

### 动态模式拓扑加载
- 模式拓扑文件在运行时从 `backend/modes` 加载
- 支持格式: `.yaml`、`.yml`、`.json`
- 每个模式定义可控制:
  - `root` 根节点与节点图（`LLM` / `PARALLEL` / `SEQUENTIAL`）
  - 角色级别 instruction/description
  - 可选 `fallbackMode`，用于构建失败时降级执行

### 动态技能加载
- 技能文件在运行时从 `backend/skills` 加载
- 支持格式: `.yaml`、`.yml`、`.json`
- 每个技能可配置:
  - `enabled`
  - `appliesTo` (适用的拓扑模式)
  - `instruction` (提示词叠加)
  - `tools` (绑定本地 ADK 函数工具)

### 关系型持久化（默认 SQLite）
- 通过 `ChatHistoryStore` 抽象持久化聊天请求/响应。
- 默认存储为 SQLite（`jdbc:sqlite:./nexus-agent.db`）。
- 存储层按关系型数据库抽象，可在后续替换 datasource 与实现扩展到 MySQL/PostgreSQL。

### AG-UI 协议流式通信
- 新增 AG-UI 兼容流式端点：`POST /api/v1/agui/run`（SSE 事件流）。
- 已实现事件序列：`RUN_STARTED`、`TEXT_MESSAGE_START`、`TEXT_MESSAGE_CONTENT`、`TEXT_MESSAGE_END`、`RUN_FINISHED`、`RUN_ERROR`。
- 支持多模态用户输入（`text` + `image` 内容块，图片 base64 负载）。
- 支持通过 `forwardedProps` 传递运行时配置（mode/model/userId/sessionId/skillNames）。
- 支持通过后端代理根据提供方鉴权信息（`baseUrl` + `apiKey`）动态发现模型列表。

API:
- `POST /api/v1/chat`
- `GET /api/v1/chat/history`
- `POST /api/v1/agui/run`
- `GET /api/v1/skills`
- `POST /api/v1/skills/reload`
- `GET /api/v1/modes`
- `GET /api/v1/models`
- `POST /api/v1/models/discover`

### 前端 (`frontend/`)
- Vue3 + Vite AG-UI 控制台
- 当前状态:
  - agent 运行配置面板（mode/model/user/skills/provider URL/API key）
  - 基于提供方接口的可用模型自动发现与刷新
  - AG-UI 流式聊天界面
  - 图片上传与多模态发送
  - 本地会话保存与后端历史同步
- 目的: 作为可扩展的多智能体运行控制台

## 代码组织

- `backend/src/main/java/com/nexus/agent/config`: 配置属性
- `backend/src/main/java/com/nexus/agent/domain`: 领域枚举与共享类型
- `backend/src/main/java/com/nexus/agent/modes`: 模式定义模型与注册器
- `backend/src/main/java/com/nexus/agent/skills`: 动态技能加载与工具注册
- `backend/src/main/java/com/nexus/agent/persistence`: 持久化抽象与关系型实现
- `backend/src/main/java/com/nexus/agent/service`: 编排服务与拓扑工厂
- `backend/src/main/java/com/nexus/agent/api`: REST 控制器与异常映射
- `backend/src/main/resources/sql`: 数据库 schema 脚本
- `backend/modes`: 运行时模式拓扑定义
- `backend/skills`: 运行时技能定义
- `frontend/src`: Vue 应用入口与占位 UI
- `docs`: 架构与迭代记录

## 快速开始

### 后端
```bash
cd backend
mvn spring-boot:run
```

也可以在仓库根目录直接启动:
```bash
mvn -f backend/pom.xml spring-boot:run
```

### 前端
```bash
cd frontend
npm install
npm run dev
```

### IntelliJ IDEA
- 以 Maven 工程方式打开仓库根目录（使用根目录 `pom.xml`）。
- 使用共享运行配置 `Backend Spring Boot`（来自 `.run/Backend-SpringBoot.run.xml`）可直接启动后端。

## 说明

- 实际使用前，请按 Google ADK 环境要求配置模型与鉴权。
- 本脚手架重点在架构可扩展性，不代表最终业务流程实现。
