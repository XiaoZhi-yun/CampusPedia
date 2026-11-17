# AI Prompt 历史快照（核心功能迭代 · 完整 Prompt 链）

> 大赛要求：「至少 3 个核心功能迭代时与 AI 交互的完整 Prompt 链（含你的 Prompt 提问、AI 的修改建议、以及你如何引导 AI 修复 Bug 的过程）」。
> 本文件**保留原有 5 个迭代骨架**（内容一字未删、按统一结构补强为完整链），并**新增本次会话真实发生的迭代**，共 **6 条完整链**。
> 每条链统一四段结构：「用户 Prompt（原文摘录）」→「AI 的修改建议/落地动作」→「引导修 Bug / 收敛过程」→「对应提交/产物」。
> **真实性声明**：所有「用户 Prompt」「追问」「限定范围」均引自 `docs/` 或对话记录中**真实出现过的文本**；凡未记录逐字措辞处标注「按对话记录整理」；**不编造不存在的对话、不编造数据、不编造具体 commit hash**，提交一律标注「以 git log / 对话记录为准」。
> 主要可核对来源：`docs/阶段2-架构与Prompt策略.md`、`docs/开发全流程设计方案.md`、`docs/作品说明.md`、`docs/作品介绍文档.md`、`README.md`、`.workbuddy/memory/2026-11-17.md`、`.workbuddy/style-refactor/REPORT.md`，以及本次会话对话记录。

---

## 评审速查：6 条完整链 ↔ 大赛建议主题

| 大赛建议主题 | 本文件迭代 | 现场复现（视频镜头） |
|---|---|---|
| 混合检索 + RRF | 迭代一 | `docs/演示视频分镜脚本.md` S13 |
| 流式问答 + 溯源 | 迭代二 | S2–S7 |
| 权限模型与越权修复 | 迭代三 | S8 / S14 备选卡 |
| hash 维度契约与向量库 | 迭代四 | S14 |
| 输入体验与视觉重构（只改样式不动字体） | 迭代五 | S15 |
| 全库索引与注释 + 完结复查修复 | 迭代六 | S16 |

### 原 5 个迭代骨架去向对照（全部保留）

| 原骨架 | 去向 | 补强要点 |
|---|---|---|
| 一 · 混合检索 + RRF 融合 | **迭代一**（独立） | 补 Prompt 原文、`rrfScore()` 源码 `1/(60+rank)`、空 catch 改 warn、`HybridRetrieverTest`、34/34 测试证据 |
| 二 · 流式问答 + 溯源标注 | **迭代二**（独立） | 补 System Prompt / 组装模板原文、`groupedSources`/`safeUrl`、SSE 边界修复、Vitest 7 例 |
| 三 · 权限模型 + 安全加固 | **迭代三**（独立并扩展） | 补本次会话越权 IDOR 修复、403 实测、6 例越权回归、文档口径校正 |
| 四 · Bug 修复「流式生成完不停转圈」 | **并入迭代二**「引导修 Bug / 收敛过程」 | 保留「saveMessage 在 complete() 之前」定位原文与「落库移到 complete 之后」修法原文 |
| 五 · Bug 修复「删除语料还能回答（幽灵数据）」 | **并入迭代六**「引导修 Bug / 收敛过程」 | 保留级联删除 + 存量清理原文，补 `@Transactional` 防回归与备份/行数复核证据 |

---

## 迭代一：混合检索 + RRF 融合（核心 RAG 链路）

**目标**：一次召回同时兼顾语义与关键词，用 RRF 融合两路名次，任一路失败都不影响主流程。

**「用户 Prompt（原文摘录）」**
- 「请实现 HybridRetriever：pgvector 向量检索 + MySQL FULLTEXT(ngram) 关键词检索 + RRF 融合两路排名，返回带文档元数据的结果列表；两路检索各自 try-catch，互不影响。」—— 原《AI-Prompt 历史快照》迭代一原文（按对话记录整理，见本文件 git 历史）
- 「请用 Spring Boot 实现一个混合检索器：pgvector 向量检索 + MySQL 全文检索，用 RRF 融合两路得分，返回带文档元数据的检索结果。要求：检索失败要 try-catch 兜底，不要影响主流程。」—— 原《演示视频分镜脚本》示例 A 原文
- 「检索服务（Top-K + 阈值）」「混合检索：向量(pgvector) + 关键词(MySQL 全文 ngram) + RRF 融合」—— `docs/作品说明.md` 三·核心功能；`docs/阶段2-架构与Prompt策略.md` §4.3 检索参数（切片 300–500 字、重叠 50、Top-K 6、阈值 0.5）
- 「每个功能模块一条完整 Prompt 链……1. 需求描述（人类清晰表达要什么）2. AI 生成初版 3. 人工 review（架构、边界、异常）4. 引导修 Bug / 重构（针对具体问题给反馈）5. commit（对应一次提交）」—— `docs/阶段2-架构与Prompt策略.md` §五（本文件四段结构的依据）

**「AI 的修改建议/落地动作」**
- 新建 `backend/src/main/java/com/campuspedia/rag/HybridRetriever.java`，构造函数注入 4 个依赖：`PgVectorStore`（pgvector 近邻）、`EmbeddingService`（问题编码）、`DocChunkMapper.fullTextSearch`（MySQL ngram 关键词）、`KbDocumentMapper`（补全文档名 / 分类 / 条款号 / 来源链接）。
- `retrieve(query, topKRecall, hybridEnabled)`：两路分别包在 `try-catch` 内，失败只记 warn 并以另一路结果继续；`hybridEnabled=false` 时退化为纯向量检索。
- RRF 由 `rrfScore(vectorRank, keywordRank)` 累加 `1.0/(60+rank)`（源码 110–116 行），只依赖名次、无需对齐两路分数尺度；结果按融合分降序（源码 106 行 `result.sort(...)`）。
- 返回 `List<RetrievedChunk>`，携带 `vectorId / content / documentId / fileName / category / clauseRef / sourceUrl / score`；同包配套 `RetrievedChunk.java`、`VectorHit.java`、`RerankResult.java`、`PgVectorStore.java`、`QueryRewriter.java`、`RerankService.java`、`RagChain.java`。

**「引导修 Bug / 收敛过程」**
- 用户追问与限定（原快照「我的引导修正」原文，按对话记录整理）：「补齐 metadata JSON 解析、融合后按得分降序排序」——对应源码 `toChunk()` 中 `ObjectMapper` 解析 `metadataJson` 与第 106 行排序。
- 本次会话第四轮「全项目前后端只读复查」发现 P0：`HybridRetriever` 空 catch 静默吞错（`.workbuddy/memory/2026-11-17.md` 第四轮六类阻塞②）。用户用「只修改现有文件、不新建任何文件」把范围钉死，要求「空 catch 改 warn」（第六轮）。
- AI 给出的证据：测试类 `HybridRetrieverTest`（RRF 融合得分、向量维度契约）；`./mvnw -B test` 19/19 → 34/34 BUILD SUCCESS；`git diff` 核对 `rag` 包分层；可现场打开 `HybridRetriever.java` 第 110–116 行核对 `1.0/(60+rank)`（视频 S13）。

**「对应提交/产物」**
- 提交主题：`feat: 混合检索/RRF 融合检索器`（**以 `git log --oneline` / 对话记录为准**）。
- 产物：`backend/src/main/java/com/campuspedia/rag/HybridRetriever.java`、`RetrievedChunk.java`、`PgVectorStore.java`、`QueryRewriter.java`；测试类 `HybridRetrieverTest`；`docs/阶段2-架构与Prompt策略.md` §4.3/§4.6、`docs/rag.png`、`docs/作品说明.md`「核心功能」、`docs/演示视频分镜脚本.md` S13。
- **不记录具体 commit hash。**

---

## 迭代二：流式问答 + [来源N] 溯源标注（含骨架四「流式生成完不停转圈」）

**目标**：SSE 逐字输出、结论标 [来源N] 且可点击溯源；并修掉「生成完不停转圈」这一顽固 Bug。

**「用户 Prompt（原文摘录）」**
- 「问答改 SSE 流式（SseEmitter 分块推送 + fetch 流读取逐字渲染），答案末尾标 [来源N]，前端来源分组展示、点击跳转。」—— 原《AI-Prompt 历史快照》迭代二原文（按对话记录整理）
- System Prompt 原文（`docs/阶段2-架构与Prompt策略.md` §4.1）：「你是「学事通」CampusPedia，河南理工大学校园学生事务智能问答助手。你的知识全部来自学校公开制度文件（学生手册等），只回答校园学生事务问题。回答规则：1. 只依据下方【参考资料】作答，严禁使用参考资料之外的信息编造。2. 若资料不足以回答，明确回复："根据现有制度文件暂未找到明确依据，建议咨询相关部门"，不要硬答。3. 回答要分点、简洁、先给结论；涉及金额/比例/时限/流程步骤的关键信息必须与原文逐字一致。4. 每个结论后用 [来源N] 标注出处……」
- 问题组装模板原文（同文件 §4.2）：「【参考资料】[来源1] 《河南理工大学学生学籍管理规定（修订）》第四章第十三条：……【学生问题】{question} 请依据上述资料回答，结论后标注 [来源N]。」
- 「浏览器打字提问，答案逐字流式出现，无卡死」「回答后可展开看到检索片段与相似度，答案 [来源N] 可点击跳官网」—— `docs/开发全流程设计方案.md` 阶段 3 / 阶段 4 验收
- 骨架四修 Bug 的 Prompt 原文：「把会话落库移到 complete() 之后，前端收到 done 事件立即停止循环。」—— 原《AI-Prompt 历史快照》迭代四；「流式问答结束后，前端不会自动停止，一直转圈。我发现是后端 saveMessage 在 emitter.complete() 之前执行导致的阻塞。请把落库移到 complete 之后。」—— 原《演示视频分镜脚本》示例 B 原文
- 意图收敛原文：「来源只显示被引用编号、朗读时剥离 [来源N]、修正 SSE 换行边界」—— 原快照迭代二「我的引导修正」；「引用标注从『每句话标注』优化为『仅首次引用标注一次』」—— `docs/作品介绍文档.md` 三·3「Prompt 迭代测试」

**「AI 的修改建议/落地动作」**
- 后端 `ChatController.stream`（`SseEmitter`，先发 sources 再流式 answer）、`rag/RagChain.java`（`stream` 编排）；前端 `client/src/views/Chat.vue`（`fetch` 流读取、逐字渲染、`receivedDone` 提前 break 停止循环）。
- 溯源：`groupedSources` 分组来源、`[来源N]` 高亮可点击、`RagChain.safeUrl()` + `groupedSources` 双层协议白名单、`renderAnswer` 只替换标签外文本、`onCiteClick` 按 `data-idx` 定位（见 `.workbuddy/memory/2026-11-17.md` 第六轮）。
- 本次会话第六轮补齐 SSE 边界：`CRLF` 在拼接后归一化、尾帧 flush、心跳 noop、多行 `data`、content-type 校验、流式切换会话竞态防护；`RagChain` 用局部 `AtomicBoolean alive` 在客户端断开时中断生成与落库。
- 前端 `client/src/api/request.ts` 统一 `code!==200` reject、401 清登录态跳登录（排除 auth 端点）、全局 `ElMessage` 提示；`Login.vue` / `Profile.vue` 去掉重复提示。

**「引导修 Bug / 收敛过程」**
- **骨架四（生成完不停转圈）**：用户**先定位到行、再提问**——「saveMessage 在 emitter.complete() 之前同步执行导致阻塞」（原快照迭代四「我的定位」），再给「把落库移到 complete() 之后」的修法；AI 调整执行顺序 + 前端 `receivedDone` 提前 break，问题解决。这是「先证据、后修法」的典型收敛，不靠猜测。
- **引用标注的意图收敛**：System Prompt 从「结论后标 [1][2]」迭代到「仅首次引用标注一次」，通过人工评测对比生成质量（`docs/作品介绍文档.md` 三·3）；追问点集中在「来源只显示被引用编号」「朗读时剥离 [来源N]」「SSE 换行边界」。
- AI 给出的证据：`client/src/api/request.test.ts`（Vitest 7 例：token 注入、`code≠200` 拒绝、401 登出与 auth 例外、错误提示）；`npm test` 7/7、`npm run build` 通过、`./mvnw -B test` 34/34；浏览器实测逐字输出且生成完加载态自动停止（视频 S5）。

**「对应提交/产物」**
- 提交主题：`feat: rag qa + citation`、`feat: streaming chat`、`feat: trace & citation highlight`、`fix: 流式生成完自动停止`（**以 `git log --oneline` / 对话记录为准**）。
- 产物：`ChatController.java`、`rag/RagChain.java`、`client/src/views/Chat.vue`、`client/src/api/request.ts` + `request.test.ts`、`docs/阶段2-架构与Prompt策略.md` §4.1/§4.2/§4.6、`docs/开发全流程设计方案.md` 阶段 3/4、`docs/演示视频分镜脚本.md` S5–S7。
- **不记录具体 commit hash。**

---

## 迭代三：权限模型与会话越权（IDOR）修复（骨架三补强 + 本次会话）

**目标**：JWT 无状态鉴权、权限变更即时生效；补上「前端传 sessionId 不校验归属」的越权修复。

**「用户 Prompt（原文摘录）」**
- 「JWT HS256 签发 + 过滤器解析后实时查库校验状态/角色，普通用户/管理员两级，管理接口限 ADMIN。」—— 原《AI-Prompt 历史快照》迭代三原文（按对话记录整理）
- 「会话读写与流式问答均校验归属，未登录不可访问」「越权读写返回 403」「JWT 鉴权实时查库，权限变更即时生效」—— `docs/作品说明.md` 三、五；`docs/作品介绍文档.md` 二
- 本次会话真实原句：「只修改现有文件、**不新建任何文件**、**不撰写/补充项目文档**、最后提交 git 但不推送。」（`.workbuddy/memory/2026-11-17.md` 第六轮用户约束）；「用户「先整 1,2」视为解除上一轮「不新建文件 / 不写文档」约束」（第七轮原文）；「用户三问：① 直接清理 `t_chat_message` 删不掉；② `t_kb_category` 应有「其他」且前端要照应；③「2 个待推送提交」是什么。」（第八轮原文）
- 流程约束原文：「按 dev-discipline 强制 ANALYZE→PLAN→CONFIRM：只读盘点后用 AskUserQuestion 请用户确认」（`.workbuddy/memory/2026-11-17.md` 第十三轮）

**「AI 的修改建议/落地动作」**
- `JwtUtil`（HS256 + `validateSecret()` 空密钥启动 fail-fast）、`JwtAuthenticationFilter`（解析后实时查库校验状态/角色，无效 token 改 debug 日志）、`SecurityConfig`（`anyRequest().authenticated()`）、`RateLimitFilter`（登录/注册 10 次/分并清理过期计数）。
- 越权修复（本次会话第六轮）：`SessionController.ownedSession()` 三端点校验 owner；`ChatController` 校验前端传入 `sessionId` 归属；`GlobalExceptionHandler` 返回真实 HTTP 状态 + 脱敏 + `HttpMessageNotReadableException`→400；`KnowledgeService.sanitizeFileName()` + 落盘 `normalize().startsWith(dir)` 防路径穿越；`KnowledgeController` 加扩展名/空文件/sourceUrl 协议白名单与批量上限 50。
- 前端 `sessionStorage`（解决多标签串号，原快照迭代三「我的引导修正」）、`request.ts` 401 清登录态。
- 文档校正（第七轮）：`docs/作品说明.md`、`docs/作品介绍文档.md` 将「三级权限/游客模式」改为「两级权限 + 会话归属校验」，补 Redis 未启用、降级边界（生成仍需 DeepSeek 密钥），回填 34 例 / 7 例与 CI 现状；`README.md` 重写「快速开始」补 `.env` 步骤（明确 Spring Boot 不读 `.env`）。

**「引导修 Bug / 收敛过程」**
- 用户追问与限定（原快照迭代三原文）：「改实时查库（解决权限变更不生效）、sessionStorage（解决多标签串号）」。
- 本次会话第四轮：用户要求「全项目前后端只读复查」，AI 用 5 个技能 + 3 个 Explore 子代理只读复查 61 个 Java 文件 / 15 个前端源码 / 配置 / 测试，**全程未改代码**（`git status` 仍是 10 个 `M` + `.workbuddy/`），定位六类 P0，其中①即越权 IDOR：`SessionController.java:54-75` 三端点只校验登录不校验 owner、`ChatController.java:36`→`RagChain.java:170-192` 前端传 `sessionId` 不校验归属。
- 收敛：用户「先整 1,2」解除约束后，第七轮新建 `SessionControllerTest`（6 例：越权读 / 改名 / 删除、会话不存在、owner 正常、匿名 401）与 `KnowledgeServiceTest`（9 例：文件名净化 / 路径穿越 / 落盘边界，用 `@TempDir` + `ReflectionTestUtils`）。坑位（按对话记录整理）：MyBatis-Plus `updateById` 有 `T / Collection<T>` 重载导致 `argThat` 推断歧义 → 改 `ArgumentCaptor`；Mockito 对 `Long` 默认返回 `0L` 而非 null，匿名用例需显式 `thenReturn(null)`。
- AI 给出的证据：临时后端 8082 实测 `/api/notice` 200、`/api/admin/users` 与未知端点 403、登录空 body 400 且不再回显控制器方法签名、登录第 10 次起 429（临时实例已停止、用户 8081 未触碰）；`./mvnw -B test` 19/19（含「会话越权」3 条回归）→ 34/34；`npm test` 7/7；`npm run build` 通过。

**「对应提交/产物」**
- 提交主题：`feat: 权限模型/JWT 实时鉴权`、`fix: 全项目完结阻塞项修复与前端视觉重构收口`、`docs+test: 补齐部署文档、越权/上传回归测试与 CI`（**以 `git log --oneline` / 对话记录为准**）。
- 产物：`security/*`、`SessionController.java`、`ChatController.java`、`GlobalExceptionHandler.java`、`SessionControllerTest`、`KnowledgeServiceTest`、`docs/作品说明.md`、`docs/作品介绍文档.md`、`README.md`、`.github/workflows/ci.yml`、`docs/演示视频分镜脚本.md` S8 / S14 备选卡。
- **不记录具体 commit hash。**

---

## 迭代四：hash 维度契约与向量库（本次会话新增）

**目标**：修掉「免密钥部署语料永远无法入库、且不报错」的隐性契约不一致。

**「用户 Prompt（原文摘录）」**
- 只读复查结论原文（`.workbuddy/memory/2026-11-17.md` 第四轮，用户要求「只读复查、给证据、不要猜」后 AI 交出的定位）：「② 默认 `provider:hash` 128 维 vs `sql/init_pgvector.sql:6 vector(1024)`，免密钥部署语料永远无法入库且 `HybridRetriever` 空 catch 静默吞错」—— 用户据此发起修复。
- 范围限定原文（同文件第六轮）：「只修改现有文件、**不新建任何文件**、**不撰写/补充项目文档**、最后提交 git 但不推送。因此测试补在现有 3 个测试类内」。
- 部署口径原文（`README.md`「快速开始」，第七轮按用户「先整 1,2」补写）：「默认 `EMBEDDING_PROVIDER=hash`（免密钥、离线可跑）；hash 与 dashscope 两种 provider **都输出 1024 维**，与 `sql/init_pgvector.sql` 的 `vector(1024)` 一致」；「pgvector 建表（`vector(1024)`，维度需与 `campus.embedding.dimensions` 一致）」。
- 本次会话任务原文（同一「证据化」意图控制仍在用）：「不编造不存在的对话或数据；不确定的地方写「按对话记录整理」。」

**「AI 的修改建议/落地动作」**
- `HashEmbeddingService` / `DashScopeEmbeddingService` 维度统一由 `campus.embedding.dimensions` 注入（默认 1024，与 `vector(1024)` 一致），消除 128 维与 1024 维的隐式分歧。
- `HybridRetriever` 空 catch 改 warn（不再静默吞错）；级联删除加 `@Transactional` + 失败 warn 日志。
- `JwtUtil.validateSecret()` 启动 fail-fast；`SecurityConfig.anyRequest().authenticated()`；`GlobalExceptionHandler` 返回真实 HTTP 状态 + 脱敏。
- 移除 `ALTER TABLE ... #{n}` 的 `resetAutoIncrement` / `selectMaxId`（4 个文件）。

**「引导修 Bug / 收敛过程」**
- 用户如何追问 / 限定（**按对话记录整理**）：先要求「只读复查、不改代码、给出证据、不要猜」，拿到第四轮六类 P0 后，用第六轮约束把范围钉死为「只改现有文件、测试补在现有 3 个测试类内」，再要求「维度统一由配置注入、空 catch 改 warn」并附带回归用例。全程没有让 AI「直接改」。
- AI 如何给证据：`./mvnw -B test` **19/19 通过**（新增「维度契约」「空密钥 fail-fast」「会话越权」3 条回归）；启动临时后端 8082 实测 `/api/notice` 200、`/api/admin/users` 403、登录空 body 400、第 10 次起 429，临时实例已停止、用户 8081 未触碰；`npm run build` 通过（TEMP 重定向到 `client/.tmp/`，宿主沙箱对系统 Temp 删除限制，属环境问题非代码缺陷）。
- 后续收敛（第七轮）：`mvnw -B test` 34/34、`npm test` 7/7、`npm run build` 通过、`npm ci --dry-run` 锁文件一致。

**「对应提交/产物」**
- 提交主题：`fix: 全项目完结阻塞项修复与前端视觉重构收口`（**以 `git log --oneline` / 对话记录为准**）。
- 产物：`service/HashEmbeddingService.java`、`service/DashScopeEmbeddingService.java`、`rag/HybridRetriever.java`、`sql/init_pgvector.sql`、`README.md`「快速开始」、`docs/作品介绍文档.md` 一·1.2；`.workbuddy/memory/2026-11-17.md` 第四 / 六轮；`docs/演示视频分镜脚本.md` S14。
- **不记录具体 commit hash。**

---

## 迭代五：输入体验与视觉重构（只改样式不动字体 / 脚本）（本次会话新增）

**目标**：修「输入框双层橙框」等体验问题、重做视觉，但**字体与脚本逐字节不变**，且每次改动自带一键回退补丁。

**「用户 Prompt（原文摘录）」**
- 「需求：修复登录/注册样式；全项目移除输入框内嵌套的「高亮容器」，恢复简洁单层结构；恢复原大屏背景图；保留功能与**上一版字体（字体/字号/字重/字体颜色均不变）**；增强视觉冲击力。」—— `.workbuddy/memory/2026-11-17.md` 第二轮原文
- 「诉求：AI 回复气泡太宽、不符合使用习惯。」（第五轮）；「需求：会话项编辑/删除按钮太小；聊天消息左右留白过宽、被挤到中间；消息满屏后流式回答时下拉顿挫；个人中心页面没占满屏幕。」（第三轮）—— 同文件原文
- 「不改 template/script/路由/接口/后端」「不引入外部字体或新依赖（不能改 `index.html`/`main.ts`/`package.json`，且需离线可用）」—— `.workbuddy/style-refactor/REPORT.md` §0 Constraints 原文
- 本次会话任务原文（同一意图控制方法此刻仍在用）：「只改 docs/演示视频分镜脚本.md 与 docs/AI-Prompt历史快照.md 这两个文件（简体中文）。不要修改其他文件，不要运行 git/构建命令。」

**「AI 的修改建议/落地动作」**
- 第一轮（杂志编辑 / Editorial 方向）：`client/src/style.css` 整文件重写（89 行 → 约 460 行），集中 `--cp-*` 设计令牌、明暗双主题、Element Plus 变量映射；9 个 `.vue` **只改 `<style scoped>`**；「书脊」竖线、衬线展示字、单一朱橘 `#ea580c`。用 `apply_styles.py` 从原始副本只替换 style 块，并由 `verify.py` 当场断言非样式部分不变。
- 第二轮（输入框单层化）：`style.css` 输入框统一「透明底 + 唯一一层 1px inset 描边，hover/focus 只换色」；`:focus-visible` 排除 `input/textarea/select/.el-input__wrapper`；`.el-input__inner` 显式 `outline:none`；`Login.vue` 恢复 Unsplash 大图（带兜底色）、暗角 / 双色光晕 / 玻璃卡片外发光与错峰入场；`Chat.vue`、`Config.vue` 去掉人为加高的 padding 与 `min-height:32px`。
- 第三 / 五轮：`--cp-read-max` 880→1240、移除消息行动画、`.msg-list` 加 `overflow-anchor:none / overscroll-behavior:contain / scroll-behavior:auto / contain:layout paint`、`.sop` 改 32×32 点击区、`Profile.vue` 撑满视口；第五轮新增 `--cp-answer-max: 560px` 与 `.msg.assistant .bubble, .msg.assistant .sources { max-width: min(100%, var(--cp-answer-max)); }`（`min(100%, …)` 保证窄屏退化为 100%，只作用于 assistant）。
- **明确未做**（按对话记录如实交接）：流式每个 `delta` 在 script 中 `md.render` 全量重渲染并强制 `scrollTop` 属脚本层开销，延续「功能 / 交互逻辑不变」约束**未改任何 script**；若仍顿挫需一处最小脚本优化（rAF 节流 `scrollBottom` 或仅在贴近底部时自动滚动），但会打破「script 逐字节不变」验证链，须用户明确授权后再做。

**「引导修 Bug / 收敛过程」**
- 用户如何限定范围：用「只改样式」「字体 / 字号 / 字重 / 字体颜色均不变」「不引外链 / 不改入口文件」三条硬约束把 AI 锁在样式层；第三轮后还形成「不改 script」的隐性约束，AI 主动在交接里写明剩余瓶颈、未擅自越界。
- AI 如何给出证据（构建 / 测试 / 截图 / 字节级校验）：
  - `verify.py` 全 PASS：9 个 `.vue` 的 `<style scoped>` 之前与 `</style>` 之后**逐字节等于原始备份**；`style.css` 花括号 89/89 平衡、无外链资源。
  - 与 v1 / v2 / v3 基线比对 **226 条 `font-family / font-size / font-weight / color` 声明，差异 0**（第二、三、五轮各验一次）。
  - `npm run build` 通过（仅既有 `>500kB chunk` 体积警告）；`git diff --name-only` 仅 10 个 `client/src` 文件，`backend/`、`sql/`、`corpus/`、`docs/` 零改动。
  - `git apply --check` 正反向补丁均 OK；`changes.patch` / `revert.patch` 与 `v1-to-v2` / `v2-to-v3` / `v3-to-v4` 系列补丁使「最初 / v1 / v2 / v3 / 当前」**5 态回退均通过**。
  - 浏览器截图：登录页大图背景恢复、焦点态只剩单层橙线、注册页 5 个输入框同为单层透明细边框；AI 气泡由约 660px → 560px、文字 2 行 → 3 行（Chrome 在用户已登录的 `localhost:5173` 实测，标签已还原 `/` 并释放控制权）。
  - WCAG 对比度实测全部 ≥4.5:1；坑位（按对话记录整理）：`.vue` 文件末尾无换行，补丁必须用 `git diff --no-index` 生成（`difflib` 生成的补丁缺 `\ No newline at end of file` 会被 git 判为 corrupt）；patch 必须二进制写保持 LF，Windows `write_text` 写出的 CRLF 补丁 `git apply` 会失败。
- 回退材料：`.workbuddy/style-refactor/`（`ORIGINAL_STYLE_BASELINE.md`、`original/` 10 个原样副本、`new-styles/`、`apply_styles.py`、`verify.py`、`restore.sh`、正反向 patch、`REPORT.md`），首选回退 `git checkout -- <10 个 client/src 文件>`。

**「对应提交/产物」**
- 该视觉重构在 2026-11-17 前几轮**未 add / 未 commit / 未 push**（`REPORT.md` 与 `.workbuddy/memory/2026-11-17.md` 均如实记录），最终与其他阻塞项一并并入提交主题 `fix: 全项目完结阻塞项修复与前端视觉重构收口`（**以 `git log --oneline` / 对话记录为准**）。
- 产物：10 个 `client/src` 样式文件、`.workbuddy/style-refactor/` 全部证据材料、`docs/演示视频分镜脚本.md` S15。
- **不记录具体 commit hash。**

---

## 迭代六：全库索引与注释 + 完结复查修复（本次会话新增，含骨架五「幽灵数据」）

**目标**：完成全项目只读复查后的阻塞项清零、全库索引 / 注释落地、全量代码注释补齐。

**「用户 Prompt（原文摘录）」**
- 「用户要求按「索引变更语句 / 各索引添加依据 / t_sys_log 用途分析 / 表与字段注释语句」顺序输出可执行 DDL，且不改业务字段、数据与现有约束；追加提问 t_sys_log 内容能否删。」—— `.workbuddy/memory/2026-11-17.md` 第十轮原文
- 「用户「全 是」= 四项全做：运行库执行脚本、同步 `sql/init.sql`、提交并推送、代删 `t_sys_log`。」—— 同文件第十一轮原文
- 「用户要求修 `t_chat_session.title` 默认值乱码。」—— 第十二轮原文
- 「用户附加 dev-discipline / dev-god / implement 三技能要求为全项目前后端加注释……得到「全量三层注释 + 提交并推送两端」。」—— 第十三轮原文
- 骨架五原 Prompt 原文：「删除改成级联（切片+向量+物理文件），每步 try-catch 容错，一步失败不牵连其它。」—— 原《AI-Prompt 历史快照》迭代五；定位原文：「delete 只删了文档表，t_doc_chunk 和 pgvector 残留 document_id=32 的切片/向量。」（按对话记录整理）
- 第四轮复查中幽灵数据回归的 P0 原文：「⑤ 全项目零 `@Transactional` + `KnowledgeService.java:123-129` 删除空 catch → 幽灵数据回归」

**「AI 的修改建议/落地动作」**
- 骨架五（幽灵数据）：重写 `delete` 为级联删除（切片 + 向量 + 物理文件），每步 try-catch 容错，一步失败不牵连其它；清理存量幽灵数据 SQL；本次会话第六轮再加 `@Transactional` + 失败 warn 日志，从根上防回归。
- 全库索引与注释（第十 / 十一轮）：生成 `sql/migrate_indexes_comments.sql`——9 个 `ADD INDEX` + 1 个 `DROP`（`idx_doc` 是 `(document_id, chunk_index)` 的冗余最左前缀）+ 9 条表 `COMMENT` + 64 条 `MODIFY COLUMN ... COMMENT`；列定义逐字取自当前库 `SHOW CREATE TABLE` 再追加 COMMENT，确保类型 / 默认值 / collation / AUTO_INCREMENT / ON UPDATE 不变；索引依据来自真实查询（`GROUP BY file_type`、`eq(document_id).orderByAsc(chunk_index)`、`eq(user_id).orderByDesc(update_time)`、7 日趋势 `WHERE create_time>=...`、`ORDER BY top DESC,create_time ASC`）；刻意不加 `t_user`、`t_kb_category`、`idx_session`、`t_sys_config`、低基数列与 MEDIUMTEXT 分组列。
- `init.sql` 同步：用 `git show HEAD:sql/init.sql` 取回原始版本，以 `^(PRIMARY|UNIQUE|FULLTEXT|KEY|CONSTRAINT|CHECK)\b` 区分约束行重建；补显式 `COLLATE=utf8mb4_unicode_ci` 消除 0900_ai_ci 差异；`student_no` 列级 UNIQUE 改表级 `uk_student_no`。
- 全量注释（第十三轮）：80 个源码文件补三层中文注释，6 组并行子代理分组（19/18/18/7/10/7 文件）；主会话修复 3 类问题：① 第 4、6 组误判换行风格致 14 个文件被写成 CRLF → 字节级转回 LF（备份证实原本全为 LF）；② `CampusPediaApplication.java` 落在各组 Glob 外未覆盖 → 补三层注释；③ 双轴审查后修正 `DocumentLoader` 漏 `.markdown`、3 个测试类游离注释段、`App.vue` 文件头。
- 其它收尾：第八轮补 `t_kb_category` 7 条 `INSERT IGNORE`（6 大类 + id=7「其他」sort_num=99）并让 `Knowledge.vue` 分类完全由 `/api/kb/categories` 驱动；第九轮 `mysqldump` 备份后代删 18 条孤儿消息；第十二轮修 `t_chat_session.title` 默认值 mojibake（HEX `C3A6E28093…` → `E696B0E4BC9AE8AF9D`）。

**「引导修 Bug / 收敛过程」**
- 用户如何追问 / 限定：第十轮「不改业务字段、数据与现有约束」并指定 DDL 输出顺序；第十一轮「全 是」逐项授权（执行 / 同步 / 提交推送 / 代删）；第十三轮经 `AskUserQuestion` 确认「全量三层注释 + 提交并推送两端」后才动手（ANALYZE→PLAN→CONFIRM）。
- AI 如何给证据：
  - 临时库 `campus_pedia_ddl_check` 完整干跑脚本无报错；64 列定义逐列对比**完全一致**；PRIMARY / UNIQUE / FULLTEXT 集合未变；字段注释 0→64；索引 +9 / −1；临时库已 DROP 清理。
  - 运行库执行后复核：64 列定义执行前后逐列一致、索引集合 22/22、字段注释 64/64、表注释 9/9；临时空库执行 `init.sql` 成功且与运行库完全一致。
  - 备份优先：`.workbuddy/db-backup/t_chat_message_20261117_144026.sql`（23K / 18 行）、`t_sys_log_20261117_150205.sql`（12K / 114 行）可完整恢复；删后复核分类仍 7 条、语料文档仍 4 篇（user 4 / category 7 / document 4 / chunk 146 / notice 1 / config 11），未误伤。
  - 注释轮最终证据：`git diff` **80/80 文件、1751 行全为注释、0 删除 / 修改行**；零注释 33→0、注释率 3.5%→25.4%；全部 LF、末尾字节不变；`mvnw test` 34/34、`npm test` 7/7、`npm run build` 通过。
  - 如实说明的遗留（按对话记录整理）：4 处历史遗留差异（运行库 `t_chat_message/t_notice.content` 为 TEXT 而 `init.sql` 为更宽松的 MEDIUMTEXT、`t_sys_config.remark` 默认值差异、`t_user` 列顺序不同）按「不改业务字段」未强行对齐；`SessionController.create` 显式传「新对话」而 DDL 默认「新会话」，未强行统一；`作品介绍文档.docx/.pdf`、`技术文档.docx` 派生文档未同步。
- 坑位：`SHOW CREATE TABLE` 无外键 / 无触发器，事务内试删 `deleted_rows=1` 且 ROLLBACK 后仍 18 条 → 服务端可删；模拟客户端 safe mode 复现 **ERROR 1175**（`WHERE create_time < ...` 未用索引列被拒），改用主键 `id` 条件即通过。

**「对应提交/产物」**
- 提交主题：`fix: 补齐语料「其他」分类与前端映射，修正分类表初始化`、`db: 补全全库索引与表/字段注释，并同步初始化脚本`、`docs: 为前后端全部源码补齐三层中文注释`、`fix: 全项目完结阻塞项修复与前端视觉重构收口`、`docs+test: 补齐部署文档、越权/上传回归测试与 CI`（**以 `git log --oneline` / 对话记录为准**）。
- 产物：`sql/migrate_indexes_comments.sql`、`sql/init.sql`、`Knowledge.vue`、`SessionController.java`、`KnowledgeService.java`、`client/src/**` 全部注释、`docs/作品介绍文档.md`、`.workbuddy/db-backup/`、`.workbuddy/memory/2026-11-17.md`；`docs/演示视频分镜脚本.md` S16。
- **不记录具体 commit hash。**

---

## 附：提交统计与复现性说明

### 1. 提交统计

- **99 次细粒度提交**（参赛材料登记口径；**以 `git log --oneline` 实测为准，按对话记录整理**）。
  - 本文件上一版记为 92 次，差额来自 2026-11-17 完结复查与收尾阶段新增的细粒度提交；对话记录中可见的提交主题包括「全项目完结阻塞项修复与前端视觉重构收口」「补齐部署文档、越权/上传回归测试与 CI」「补齐语料『其他』分类与前端映射」「补全全库索引与表/字段注释」「为前后端全部源码补齐三层中文注释」等。
  - 精确总数请运行 `git log --oneline | wc -l` 核对；本文件**不把 99 当作已实测断言**。
- **每次提交对应一次 AI 对话或一个功能模块**：按 `docs/阶段2-架构与Prompt策略.md` §五「每个功能模块一条完整 Prompt 链……5. commit（对应一次提交）」执行；**首提交仅 5 个文件，非『一次性 initial commit』**（按对话记录整理）。
- 提交描述为**中文描述 + 英文前缀**：`feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `style:` / `chore:` / `db:`。
- **不记录具体 commit hash**：本文件与参赛材料一律以「提交主题」描述，具体 hash **以 `git log --oneline` 为准**，未编造任何 hash。

### 2. docs/ 下参赛材料清单

| 文件 | 用途 | 备注 |
|---|---|---|
| `docs/作品介绍文档.md` | 作品介绍（项目详情、使用方式、亮点、AI 测试、关键技术） | 最新口径；`.docx` / `.pdf` 为派生文件 |
| `docs/作品介绍文档.docx` / `.pdf` | 作品介绍派生版 | 2026-11-17 校正后**尚未重新导出**，以 `.md` 为准（按对话记录整理） |
| `docs/技术文档.docx` | 技术文档 | 派生文件，同上 |
| `docs/作品说明.md` | 作品说明（简介、问题、功能、架构、创新点、工程可复现性、演示要点） | 已校正为两级权限 + 归属校验 |
| `docs/演示视频分镜脚本.md` | **演示视频分镜 + 台词 + 录制规范 + 评审 checklist**（本次重写） | 与本文档 S13/S14/S15 互证 |
| `docs/AI-Prompt历史快照.md` | **核心功能迭代完整 Prompt 链**（本文件，本次重写） | 大赛「≥3 条完整链」对应材料 |
| `docs/开发全流程设计方案.md` | 分阶段开发、边界测试、验收标准 | 阶段 0–6 里程碑 |
| `docs/阶段2-架构与Prompt策略.md` | 整体架构、RAG System Prompt、组装模板、检索参数、Prompt 链定义 | 本文件四段结构的依据 |
| `docs/语料清单.md` | 38 篇河南理工大学制度文件摸底（6 大类） | 官网公开版定位 |
| `docs/agent-memory.md` | 早期修复记录（向量化失败、SSE 异常兼容性） | 2026-11-03 |
| `docs/arch.png` / `docs/rag.png` | 系统架构图 / RAG 流水线图 | 视频 S3/S13 可引用 |
| `README.md`（仓库根目录） | 10 分钟复现指南、`.env` 与环境变量导出、验证命令、CI | 复现入口 |
| `corpus/README.md` | 语料目录说明与隐私红线 | 仅收录公开制度文件 |
| `.workbuddy/memory/2026-11-17.md` | 本次会话逐轮对话记录（13 轮） | 未跟踪，本地可核，本文件主要事实来源 |
| `.workbuddy/style-refactor/REPORT.md`、`ORIGINAL_STYLE_BASELINE.md` | 视觉重构方案、验证结果、5 态回退手册 | 未跟踪，本地可核 |
| `.github/workflows/ci.yml` | CI（后端 `mvnw test` + 前端 `npm ci` / `npm test` / `npm run build`） | 仓库根目录 |

### 3. 如何核对

```bash
git log --oneline                  # 逐条核对每次提交主题与细粒度
git log --oneline | wc -l          # 提交总数（对照 99）
git log --oneline -- docs/         # 参赛材料的提交轨迹
git show --stat <提交主题或 hash>   # 单次提交改动了哪些文件
git log --oneline --grep='fix:'    # 按英文前缀筛选某类迭代
```

- **完整 Prompt 链核对**：本文件 6 条链 ↔ `docs/演示视频分镜脚本.md` ④ AI 协同段（S13 / S14 / S15）↔ `docs/阶段2-架构与Prompt策略.md` §五 ↔ `.workbuddy/memory/2026-11-17.md` ↔ `.workbuddy/style-refactor/REPORT.md`，可交叉验证「Prompt → 落地动作 → 引导收敛 → 证据 → 提交」是否齐全。
- **验证证据核对**：`./mvnw -B test`（34/34）、`npm test`（7/7）、`npm run build`、`.github/workflows/ci.yml`；测试通过数等数值以 CI / 本地实测为准（按对话记录整理）。
- **复现环境核对**：按 `README.md`「快速开始」从零部署；注意 `docker compose` 会读 `.env` 而 **Spring Boot 不读 `.env`**，后端需另行把变量导出到 shell；`hash` 与 `dashscope` 两种 provider 均为 1024 维，与 `vector(1024)` 一致。
- **真实性声明**：全文「用户 Prompt」「引导修 Bug」均引自 `docs/` 或对话记录中真实出现过的文本；凡未记录逐字措辞处标注「按对话记录整理」；**不编造不存在的对话、不编造数据、不编造具体 commit hash**。不确定的数量与结论一律以 `git log` / CI 实测 / 对话记录为准。
