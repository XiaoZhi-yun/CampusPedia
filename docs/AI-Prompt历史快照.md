# AI Prompt 历史快照（核心功能迭代）

> 本快照基于 92 个 Git 提交与 AI 对话历史，选取 5 个核心功能迭代，还原完整 Prompt 链（提问 → AI 建议 → 引导修复 Bug）。

## 迭代一：混合检索 + RRF 融合（核心 RAG 链路）

**目标**：让检索同时兼顾语义与关键词，用 RRF 融合两路得分。

**Prompt 提问**：请实现 HybridRetriever：pgvector 向量检索 + MySQL FULLTEXT(ngram) 关键词检索 + RRF 融合两路排名，返回带文档元数据的结果列表；两路检索各自 try-catch，互不影响。

**AI 修改建议**：生成构造函数注入四依赖、两路独立检索、RRF 公式 1/(60+rank) 的骨架。

**我的引导修正**：补齐 metadata JSON 解析、融合后按得分降序排序。

## 迭代二：流式问答 + 溯源标注

**目标**：答案流式输出，结论后标注 [来源N]，可点击溯源。

**Prompt 提问**：问答改 SSE 流式（SseEmitter 分块推送 + fetch 流读取逐字渲染），答案末尾标 [来源N]，前端来源分组展示、点击跳转。

**我的引导修正**：来源只显示被引用编号、朗读时剥离 [来源N]、修正 SSE 换行边界。

## 迭代三：权限模型 + 安全加固

**目标**：JWT 无状态鉴权，权限变更即时生效。

**Prompt 提问**：JWT HS256 签发 + 过滤器解析后实时查库校验状态/角色，普通用户/管理员两级，管理接口限 ADMIN。

**我的引导修正**：改实时查库（解决权限变更不生效）、sessionStorage（解决多标签串号）。

## 迭代四：Bug 修复——流式生成完不停转圈

**我的定位**：saveMessage 在 emitter.complete() 之前同步执行导致阻塞。

**Prompt 提问**：把会话落库移到 complete() 之后，前端收到 done 事件立即停止循环。

**结果**：调整执行顺序 + 前端 receivedDone 提前 break，问题解决。

## 迭代五：Bug 修复——删除语料还能回答（幽灵数据）

**我的定位**：delete 只删了文档表，t_doc_chunk 和 pgvector 残留 document_id=32 的切片/向量。

**Prompt 提问**：删除改成级联（切片+向量+物理文件），每步 try-catch 容错，一步失败不牵连其它。

**结果**：重写 delete + 清理存量幽灵数据 SQL，验证通过。

## 附：Git 提交统计

- 提交总数 92 个，细粒度（每功能/修复一个 commit）
- 首提交仅 5 个文件，非「一次性 initial commit」
- 中文描述 + 英文前缀（feat:/fix:/refactor:/docs:/test:/style:/chore:）