-- 1. 索引变更语句
-- 说明：只调整非约束性的普通二级索引；PRIMARY / UNIQUE / FULLTEXT 一律保留，
--       业务字段、数据、默认值、字符集与现有约束均不改变。
ALTER TABLE `t_kb_document`
  ADD INDEX `idx_category_id` (`category_id`),
  ADD INDEX `idx_file_type` (`file_type`);

-- 索引替换：原 idx_doc(document_id) 是下列联合索引的最左前缀，保留即冗余，故一并移除。
ALTER TABLE `t_doc_chunk`
  DROP INDEX `idx_doc`,
  ADD INDEX `idx_doc_chunk` (`document_id`, `chunk_index`);

ALTER TABLE `t_chat_session`
  ADD INDEX `idx_user_update` (`user_id`, `update_time`);

ALTER TABLE `t_chat_message`
  ADD INDEX `idx_user` (`user_id`),
  ADD INDEX `idx_create_time` (`create_time`);

ALTER TABLE `t_notice`
  ADD INDEX `idx_top_create` (`top` DESC, `create_time` ASC);

ALTER TABLE `t_sys_log`
  ADD INDEX `idx_log_user_action_time` (`username`, `action`, `create_time`),
  ADD INDEX `idx_log_create_time` (`create_time`);

-- 2. 各索引添加依据
-- t_kb_document.idx_category_id(category_id)  关联字段（→ t_kb_category.id），支撑按分类筛选/统计；现无索引。
-- t_kb_document.idx_file_type(file_type)      StatsController/KbDocumentMapper 的 GROUP BY file_type 文档类型分布。
-- t_doc_chunk.idx_doc_chunk(document_id,chunk_index)  eq(document_id).orderByAsc(chunk_index)（切片预览）
--                                           与 delete(eq(document_id))（级联删）；等值关联列在前、排序列在后。
-- t_chat_session.idx_user_update(user_id,update_time) eq(user_id).orderByDesc(update_time)（会话列表）
--                                           与 delete(eq(user_id))（级联删用户）；InnoDB 可反向扫描满足 DESC。
-- t_chat_message.idx_user(user_id)             UserController 级联删除 delete(eq(user_id))，关联 t_user.id。
-- t_chat_message.idx_create_time(create_time)  WHERE create_time >= DATE_SUB(...) GROUP BY DATE(create_time)（7 日趋势），
--                                            并支撑按时间清理旧数据（此前 safe update 报 ERROR 1175 即因该列无索引）。
-- t_notice.idx_top_create(top DESC,create_time ASC)  精确匹配 ORDER BY top DESC, create_time ASC 的混合方向排序，避免 filesort。
-- t_sys_log.idx_log_user_action_time(username,action,create_time) 审计常见条件：按操作人、按操作人+类型，末列支撑时间排序。
-- t_sys_log.idx_log_create_time(create_time)  全局按时间范围查询与定期归档；与上一索引前缀不同，不冗余。
-- 刻意不新增（避免冗余/低效索引）：
--   t_user：uk_student_no 已覆盖 student_no 等值，PRIMARY(id) 已覆盖 orderByAsc(id)；role/status 无查询条件且低基数。
--   t_kb_category：仅 7 行，ORDER BY sort_num 全表扫描优于索引。
--   t_chat_message.idx_session：已存在，InnoDB 二级索引隐含主键，已覆盖 eq(session_id).orderByAsc(id)，重复添加即冗余。
--   t_sys_config：主键等值查询 + 全表 11 行配置读取，无需二级索引。
--   低基数列（role/action/status/vector_status）与 MEDIUMTEXT 分组列（content）不单独建索引，收益不抵写入成本。
--   业务上 vector_id 应唯一、session_id/user_id 应为外键，但会增加约束，按要求保持不变。

-- 3. t_sys_log 表用途分析
-- 写入点：ChatController.stream() 每次提问插入一条（username=userId 或游客，role=user，
--         action=chat，detail=提问原文前 200 字，ip=请求来源，create_time=当前时间），写入失败仅 warn 不影响问答。
-- 读取点：全库无 SELECT / 无前端页面 / 无统计接口依赖它；StatsController 的问答量与热门问题来自 t_chat_message。
-- 现状：114 行，全部 action='chat'，时间 2026-11-14 19:11 ~ 2026-11-17 12:57，属于持续增长的提问日志，不是静态旧数据。
-- 结论：删除不影响任何业务功能（无外键、无读取方），但会丢失提问原文审计记录，且新提问会继续写入；
--       若清理，建议先备份并用 DELETE（可回滚），不要用 TRUNCATE（DDL 隐式提交且重置自增值）。
-- 配套：idx_log_create_time 正是为「按时间范围归档/清理」准备。

-- 4. 表注释与字段注释变更语句
-- 每条 MODIFY COLUMN 均逐字取自当前库 SHOW CREATE TABLE，仅追加 COMMENT，不改变任何列定义。

-- t_user（10 个字段）
ALTER TABLE `t_user`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键',
  MODIFY COLUMN `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  MODIFY COLUMN `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码，BCrypt 哈希',
  MODIFY COLUMN `role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'user' COMMENT '角色：user 普通用户 / admin 管理员',
  MODIFY COLUMN `status` int DEFAULT '1' COMMENT '账号状态：1 启用，0 禁用',
  MODIFY COLUMN `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '头像地址',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  MODIFY COLUMN `student_no` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学号，登录账号，唯一',
  MODIFY COLUMN `major` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业',
  MODIFY COLUMN `class_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '班级';

ALTER TABLE `t_user` COMMENT = '用户表：学生与管理员账号，登录与权限主体';

-- t_kb_category（4 个字段）
ALTER TABLE `t_kb_category`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '分类ID，主键',
  MODIFY COLUMN `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  MODIFY COLUMN `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '分类说明',
  MODIFY COLUMN `sort_num` int DEFAULT '0' COMMENT '排序号，升序展示';

ALTER TABLE `t_kb_category` COMMENT = '语料分类表：上传文档时的归类，含「其他」兜底分类';

-- t_kb_document（11 个字段）
ALTER TABLE `t_kb_document`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '文档ID，主键',
  MODIFY COLUMN `category_id` int NOT NULL DEFAULT '0' COMMENT '所属分类ID，关联 t_kb_category.id；0 表示未分类',
  MODIFY COLUMN `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名（已净化）',
  MODIFY COLUMN `file_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '文件扩展名',
  MODIFY COLUMN `file_size` int DEFAULT '0' COMMENT '文件大小，字节',
  MODIFY COLUMN `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '服务器落盘路径',
  MODIFY COLUMN `source_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '官网原文链接，仅 http/https',
  MODIFY COLUMN `vector_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '向量化状态：pending 待处理 / done 已入库 / fail 失败',
  MODIFY COLUMN `chunk_count` int DEFAULT '0' COMMENT '切片数量',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  MODIFY COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间';

ALTER TABLE `t_kb_document` COMMENT = '语料文档表：上传的制度文件及向量化状态';

-- t_doc_chunk（7 个字段）
ALTER TABLE `t_doc_chunk`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '切片ID，主键',
  MODIFY COLUMN `document_id` int NOT NULL COMMENT '所属文档ID，关联 t_kb_document.id',
  MODIFY COLUMN `chunk_index` int NOT NULL COMMENT '切片序号，从 0 开始',
  MODIFY COLUMN `content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '切片正文，MySQL ngram 全文检索字段',
  MODIFY COLUMN `char_count` int DEFAULT '0' COMMENT '切片字符数',
  MODIFY COLUMN `clause_ref` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '条款引用，法规类文档适用',
  MODIFY COLUMN `vector_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'pgvector 向量标识，关联向量库';

ALTER TABLE `t_doc_chunk` COMMENT = '文档切片表：语料切分后的检索单元，与 pgvector 向量一一对应';

-- t_chat_session（6 个字段）
ALTER TABLE `t_chat_session`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键',
  MODIFY COLUMN `user_id` int NOT NULL COMMENT '所属用户ID，关联 t_user.id',
  MODIFY COLUMN `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT 'æ–°ä¼šè¯' COMMENT '会话标题',
  MODIFY COLUMN `message_count` int DEFAULT '0' COMMENT '消息条数',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活动时间，会话列表按此倒序';

ALTER TABLE `t_chat_session` COMMENT = '会话表：用户的一次多轮对话';

-- t_chat_message（11 个字段）
ALTER TABLE `t_chat_message`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID，主键',
  MODIFY COLUMN `session_id` int NOT NULL COMMENT '所属会话ID，关联 t_chat_session.id',
  MODIFY COLUMN `user_id` int NOT NULL COMMENT '所属用户ID，关联 t_user.id',
  MODIFY COLUMN `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色：user / assistant',
  MODIFY COLUMN `content` text COLLATE utf8mb4_unicode_ci COMMENT '消息正文',
  MODIFY COLUMN `sources` text COLLATE utf8mb4_unicode_ci COMMENT '参考来源，JSON 文本',
  MODIFY COLUMN `retrieve_query` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '改写后的检索 query',
  MODIFY COLUMN `hit_count` int DEFAULT '0' COMMENT '命中切片数',
  MODIFY COLUMN `duration_ms` int DEFAULT '0' COMMENT '生成耗时，毫秒',
  MODIFY COLUMN `token_count` int DEFAULT '0' COMMENT '消耗 token 数',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间，用于按日统计与历史清理';

ALTER TABLE `t_chat_message` COMMENT = '消息表：会话内的提问与回答，含检索与生成统计';

-- t_notice（5 个字段）
ALTER TABLE `t_notice`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID，主键',
  MODIFY COLUMN `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告标题',
  MODIFY COLUMN `content` text COLLATE utf8mb4_unicode_ci COMMENT '公告正文',
  MODIFY COLUMN `top` int DEFAULT '0' COMMENT '是否置顶：1 是，0 否',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间';

ALTER TABLE `t_notice` COMMENT = '公告表：系统公告，置顶优先展示';

-- t_sys_config（3 个字段）
ALTER TABLE `t_sys_config`
  MODIFY COLUMN `config_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键，主键',
  MODIFY COLUMN `config_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置值',
  MODIFY COLUMN `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置说明';

ALTER TABLE `t_sys_config` COMMENT = '系统配置表：RAG 参数与系统提示词，键值对';

-- t_sys_log（7 个字段）
ALTER TABLE `t_sys_log`
  MODIFY COLUMN `id` int NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键',
  MODIFY COLUMN `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作者标识，当前存 userId 或「游客」',
  MODIFY COLUMN `role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作者角色',
  MODIFY COLUMN `action` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型，当前固定为 chat',
  MODIFY COLUMN `detail` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作详情，当前为提问原文前 200 字',
  MODIFY COLUMN `ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源 IP',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间，用于审计查询与定期归档';

ALTER TABLE `t_sys_log` COMMENT = '问答日志表：记录每次提问的操作审计（当前只写入、无读取接口）';
