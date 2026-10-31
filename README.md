# 学事通 · CampusPedia

校园学生事务智能问答 —— 基于 RAG 的场景化问答系统。

> 传智杯 · AI 创新应用挑战赛参赛作品

## 一句话简介

把学校公开制度文档（学籍、奖助、处分、宿舍、就业、医保等）导入后，用「混合检索 + 重排序 + 大模型」提供**流式问答 + 答案溯源引用 + 检索过程可视化**的一站式体验，支持游客 / 普通用户 / 管理员三级权限。

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
| 缓存 | Redis 7 |

## 核心功能

- 流式问答 + 答案 [来源N] 溯源引用（可点击跳转官网原文）
- 混合检索：向量(pgvector) + 关键词(MySQL 全文) + RRF 融合，再 Rerank 精排 + 相关性阈值过滤
- Query 改写 + 多轮对话上下文记忆
- 思考过程可视化（改写 query、候选条数、保留条数、阈值）
- 三级权限：游客（临时会话不落库）/ 普通用户（会话持久化）/ 管理员（后台）
- 管理后台：语料上传（md/txt/pdf/doc/docx）、向量化、参数配置、ECharts 数据大屏

## 快速开始（10 分钟复现）

### 前置要求
Docker Desktop、JDK 17、Node.js 18+

### 1. 启动基础设施
```bash
docker compose up -d   # mysql(3307) + redis(6379) + pgvector(5432)
```

### 2. 初始化数据库
```bash
# MySQL 建表（11 张）
docker exec -i campus-pedia-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 campus_pedia < sql/init.sql
# pgvector 建表
docker exec -i campus-pedia-pgvector psql -U campus -d campus_pedia < sql/init_pgvector.sql
```

### 3. 配置密钥
在 `backend/src/main/resources/` 下新建 `application-local.yml`（已 gitignore，不会提交）：
```yaml
campus:
  llm:
    deepseek-api-key: sk-你的DeepSeek密钥
  embedding:
    api-key: sk-你的DashScope密钥   # 不填则用 hash 降级，可离线跑
    provider: dashscope             # 改 hash 则免密钥
```
> 无 DashScope 密钥时，embedding 自动降级 hash、rerank 自动降级为纯混合检索，系统仍可运行。

### 4. 启动后端（端口 8081）
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 5. 启动前端（端口 5173）
```bash
cd client
npm install
npm run dev
# 浏览器打开 http://localhost:5173
```

## 目录结构

```
CampusPedia/
├── backend/               # Spring Boot 4.1 后端
│   └── src/main/java/com/campuspedia/
│       ├── rag/           # RAG 核心：RagChain/混合检索/重排/改写/向量库/文档加载
│       ├── controller/    # 10 个 REST 控制器
│       ├── service/       # 知识库/配置/LLM/Embedding 服务
│       ├── entity+mapper/ # 11 套 MyBatis-Plus 实体与映射
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
