# 学事通 · CampusPedia

校园学生事务智能问答 —— 场景化轻量 RAG 知识库。

> 传智杯 · AI 创新应用挑战赛参赛作品（B 组 · 个人赛）

## 解决什么问题

学生获取校园信息（规章制度、奖惩处罚、奖助学金与医保、就业、素质拓展学分、选课考试等）需要翻多套系统或反复问群，信息分散、口径不一。本作品把公开制度文档导入后，提供**流式问答 + 答案溯源引用 + 检索/思考过程可视化**的一站式问答体验。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | React + Vite |
| 后端 | Java + Spring Boot 3（JDK 17）|
| LLM（生成） | DeepSeek API |
| Embedding（向量化） | 千问 DashScope `text-embedding-v3` |
| 向量库 | pgvector(Docker) + 内存回退 |

## 目录结构

```
campus-rag-qa/
├── backend/    # Spring Boot 3 后端
├── frontend/   # React + Vite 前端
├── corpus/     # 教务语料（公开制度文档）
├── docs/       # 架构图、Prompt 链、技术文档
└── docker-compose.yml  # pgvector 一键启动
```

## 快速开始

> 「10 分钟极速复现指南」将随开发推进补充完整，见最终版 README。

## 语料说明

语料来自学校官网公开制度文件（仅用于教学竞赛演示，不含个人隐私数据）。详见 `corpus/README.md`。
