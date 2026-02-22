# Nexus Agent 脚手架

本仓库提供一个基于 Java 的多智能体项目脚手架，后端使用 **Google ADK**，前端使用 **Vue3**。

## 技术架构

### 后端 (`backend/`)
- 语言/运行时: Java 17
- 框架: Spring Boot + Google ADK (`com.google.adk:google-adk:0.5.0`)
- 职责: 智能体编排、技能加载与 API 暴露

支持的智能体拓扑:
- `SINGLE`: 单个 `LlmAgent` 处理完整请求
- `MASTER_SUB`: 由主 `LlmAgent` 委派给子智能体
- `MULTI_WORKFLOW`: 通过 `ParallelAgent` + `SequentialAgent` 管道进行分阶段协作

### 动态技能加载
- 技能文件在运行时从 `backend/skills` 加载
- 支持格式: `.yaml`、`.yml`、`.json`
- 每个技能可配置:
  - `enabled`
  - `appliesTo` (适用的拓扑模式)
  - `instruction` (提示词叠加)
  - `tools` (绑定本地 ADK 函数工具)

API:
- `POST /api/v1/chat`
- `GET /api/v1/skills`
- `POST /api/v1/skills/reload`
- `GET /api/v1/modes`

### 前端 (`frontend/`)
- Vue3 + Vite 占位壳层
- 当前状态: 预留了模式选择与聊天请求面板
- 目的: 在产品形态尚未确定时，保留前后端集成路径

## 代码组织

- `backend/src/main/java/com/nexus/agent/config`: 配置属性
- `backend/src/main/java/com/nexus/agent/domain`: 领域枚举与共享类型
- `backend/src/main/java/com/nexus/agent/skills`: 动态技能加载与工具注册
- `backend/src/main/java/com/nexus/agent/service`: 编排服务与拓扑工厂
- `backend/src/main/java/com/nexus/agent/api`: REST 控制器与异常映射
- `backend/skills`: 运行时技能定义
- `frontend/src`: Vue 应用入口与占位 UI
- `docs`: 架构与迭代记录

## 快速开始

### 后端
```bash
cd backend
mvn spring-boot:run
```

### 前端
```bash
cd frontend
npm install
npm run dev
```

## 说明

- 实际使用前，请按 Google ADK 环境要求配置模型与鉴权。
- 本脚手架重点在架构可扩展性，不代表最终业务流程实现。
