# 学事通 · CampusPedia

校园学生事务智能问答 —— 基于 RAG 的场景化问答系统。

> 传智杯 · AI 创新应用挑战赛参赛作品

## 一句话简介

把学校公开制度文档（学籍、奖助、处分、宿舍、就业、医保等）导入后，用「混合检索 + 重排序 + 大模型」提供**流式问答 + 答案溯源引用 + 检索过程可视化**的一站式体验，支持普通用户 / 管理员两级权限。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Pinia + ECharts |
| 后端 | Java 17 + Spring Boot 4.1 + MyBatis-Plus + Spring Security + JWT |
| LLM 生成 | DeepSeek（deepseek-chat，SSE 流式）|
| Embedding | 千问 DashScope `qwen3.7-text-embedding-flash`（1024 维，可降级为 hash 免密钥）|
| Rerank | 千问 DashScope `qwen3.7-text-rerank` |
| 向量库 | pgvector（Docker，持久化）|
| 关系库 | MySQL 8（元数据 + 全文 ngram 检索）|
| 缓存 | Redis 7（容器与依赖保留，当前业务逻辑未启用缓存）|

## 核心功能

- 流式问答 + 答案 [来源N] 溯源引用（可点击跳转官网原文）
- 混合检索：向量(pgvector) + 关键词(MySQL 全文) + RRF 融合，再 Rerank 精排 + 相关性阈值过滤
- Query 改写 + 多轮对话上下文记忆
- 思考过程可视化（改写 query、候选条数、保留条数、阈值）
- 两级权限：普通用户（会话持久化）/ 管理员（后台）
- 管理后台：语料上传（md/txt/pdf/doc/docx）、向量化、参数配置、ECharts 数据大屏

## 快速开始（10 分钟复现）

### 前置要求
Docker Desktop、JDK 17、Node.js 18+（Windows 建议 Git Bash 或 PowerShell）

### 1. 准备环境变量（必做，否则后端启动失败）
复制模板并填入真实值，`.env` 已被 gitignore，不会入库：

```bash
cp .env.example .env          # PowerShell: copy .env.example .env
```

至少设置：`MYSQL_ROOT_PASSWORD` / `POSTGRES_PASSWORD`（与数据库容器一致）、`JWT_SECRET`（**至少 32 字节随机串**，留空时后端启动即 fail-fast）、`DEEPSEEK_API_KEY`（流式生成必需）；使用千问 embedding / rerank 时再填 `DASHSCOPE_API_KEY`。

`docker compose` 会自动读取 `.env`，但 **Spring Boot 不读 `.env`**，后端进程必须单独把变量导出到当前 shell（每新开一个终端都要执行一次）：

```bash
# Git Bash / macOS / Linux（在仓库根目录执行）
set -a; . ./.env; set +a
```
```powershell
# PowerShell（在仓库根目录执行）
Get-Content .env | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') } }
```

> 也可以改用 IDE 的 Run Configuration 注入环境变量，或用已 gitignore 的 `application-local.yml` 覆盖**非密钥**配置（例如 `campus.embedding.provider`）。`application.yml` 已通过 `${MYSQL_ROOT_PASSWORD:}` 等占位符直接读取环境变量，无需把密钥写进任何文件。

### 2. 启动基础设施
```bash
docker compose up -d   # mysql(3307) + redis(6379, 预留) + pgvector(5432)
```

### 3. 初始化数据库
```bash
# MySQL 建表（9 张），密码取自 .env
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 campus_pedia < sql/init.sql
# pgvector 建表（vector(1024)，维度需与 campus.embedding.dimensions 一致）
docker compose exec -T pgvector psql -U campus -d campus_pedia < sql/init_pgvector.sql
```

### 4. 启动后端（端口 8081）
```bash
cd backend
./mvnw spring-boot:run
```
> 默认 `EMBEDDING_PROVIDER=hash`（免密钥、离线可跑）；hash 与 dashscope 两种 provider **都输出 1024 维**，与 `sql/init_pgvector.sql` 的 `vector(1024)` 一致。切到千问：导出 `EMBEDDING_PROVIDER=dashscope` 并填 `DASHSCOPE_API_KEY`。无 DashScope 密钥时 rerank 自动降级为纯混合检索；但**生成答案仍需 DeepSeek 密钥**。

### 5. 启动前端（端口 5173）
```bash
cd client
npm ci
npm run dev
# 浏览器打开 http://localhost:5173
```

### 6. 本地验证 / CI
```bash
cd backend && ./mvnw -B test     # 后端单元测试 + 越权/维度/密钥回归用例
cd client  && npm test            # 前端 vitest（请求拦截与错误处理）
cd client  && npm run build       # vue-tsc 类型检查 + 生产构建
```
CI 配置见 `.github/workflows/ci.yml`（push / PR 自动跑后端测试、前端测试与构建）。

## 目录结构

```
CampusPedia/
├── backend/               # Spring Boot 4.1 后端
│   └── src/main/java/com/campuspedia/
│       ├── rag/           # RAG 核心：RagChain/混合检索/重排/改写/向量库/文档加载
│       ├── controller/    # 10 个 REST 控制器
│       ├── service/       # 知识库/配置/LLM/Embedding 服务
│       ├── entity+mapper/ # 9 套 MyBatis-Plus 实体与映射
│       └── security/      # JWT 过滤器与鉴权
├── client/                # Vue 3 前端
├── corpus/                # 教务语料（公开制度文档）
├── sql/                   # 建表脚本（MySQL + pgvector）
└── docker-compose.yml     # 三容器一键启动
```

## 开发过程（可复现性）

完整提交历史见 `git log`（每个功能一个 commit，中文描述 + 英文前缀），从脚手架 → RAG 链路 → 混合检索 → 前端重写 → 权限模型 → 会话持久化，全程可追溯。

## 语料说明

语料来自学校官网公开制度文件（仅用于教学竞赛演示，不含个人隐私数据）。详见 `corpus/README.md`。
