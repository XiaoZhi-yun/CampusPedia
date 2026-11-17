-- 学事通 CampusPedia 数据库初始化脚本（MySQL 8，utf8mb4）

CREATE TABLE IF NOT EXISTS t_user(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID，主键',
  student_no VARCHAR(20) COMMENT '学号，登录账号，唯一',
  username VARCHAR(50) NOT NULL COMMENT '姓名',
  password VARCHAR(100) NOT NULL COMMENT '密码，BCrypt 哈希',
  major VARCHAR(50) DEFAULT '' COMMENT '专业',
  class_name VARCHAR(50) DEFAULT '' COMMENT '班级',
  role VARCHAR(20) DEFAULT 'user' COMMENT '角色：user 普通用户 / admin 管理员',
  status INT DEFAULT 1 COMMENT '账号状态：1 启用，0 禁用',
  avatar VARCHAR(255) DEFAULT '' COMMENT '头像地址',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  UNIQUE KEY uk_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表：学生与管理员账号，登录与权限主体';

CREATE TABLE IF NOT EXISTS t_kb_category(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID，主键',
  name VARCHAR(50) NOT NULL COMMENT '分类名称',
  remark VARCHAR(255) DEFAULT '' COMMENT '分类说明',
  sort_num INT DEFAULT 0 COMMENT '排序号，升序展示'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语料分类表：上传文档时的归类，含「其他」兜底分类';

-- 语料分类：与 docs/语料清单.md 的 6 大类一致，另加「其他」兜底（排序置末）。
-- INSERT IGNORE 保证重复执行 init.sql 不会覆盖已有分类，也不会产生重复行。
INSERT IGNORE INTO t_kb_category(id, name, remark, sort_num) VALUES
(1,'学籍与教学管理','学籍/选课/考试/转专业等',1),
(2,'学生管理','处分/宿舍/班级管理等',2),
(3,'学生奖助','奖助学金/评优等',3),
(4,'素质拓展','创新创业学分/素质拓展学分等',4),
(5,'学生就业','就业/实习等',5),
(6,'医疗保障','医保/体检等',6),
(7,'其他','未归入上述六类的制度文件',99);

CREATE TABLE IF NOT EXISTS t_kb_document(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID，主键',
  category_id INT NOT NULL DEFAULT 0 COMMENT '所属分类ID，关联 t_kb_category.id；0 表示未分类',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名（已净化）',
  file_type VARCHAR(20) DEFAULT '' COMMENT '文件扩展名',
  file_size INT DEFAULT 0 COMMENT '文件大小，字节',
  file_path VARCHAR(500) DEFAULT '' COMMENT '服务器落盘路径',
  source_url VARCHAR(500) DEFAULT '' COMMENT '官网原文链接，仅 http/https',
  vector_status VARCHAR(20) DEFAULT 'pending' COMMENT '向量化状态：pending 待处理 / done 已入库 / fail 失败',
  chunk_count INT DEFAULT 0 COMMENT '切片数量',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  FULLTEXT KEY ft_name (file_name) WITH PARSER ngram,
  KEY idx_category_id (category_id),
  KEY idx_file_type (file_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语料文档表：上传的制度文件及向量化状态';

CREATE TABLE IF NOT EXISTS t_doc_chunk(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '切片ID，主键',
  document_id INT NOT NULL COMMENT '所属文档ID，关联 t_kb_document.id',
  chunk_index INT NOT NULL COMMENT '切片序号，从 0 开始',
  content MEDIUMTEXT COMMENT '切片正文，MySQL ngram 全文检索字段',
  char_count INT DEFAULT 0 COMMENT '切片字符数',
  clause_ref VARCHAR(255) DEFAULT '' COMMENT '条款引用，法规类文档适用',
  vector_id VARCHAR(100) NOT NULL COMMENT 'pgvector 向量标识，关联向量库',
  KEY idx_doc_chunk (document_id, chunk_index),
  FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切片表：语料切分后的检索单元，与 pgvector 向量一一对应';

CREATE TABLE IF NOT EXISTS t_chat_session(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID，主键',
  user_id INT NOT NULL COMMENT '所属用户ID，关联 t_user.id',
  title VARCHAR(100) DEFAULT '新会话' COMMENT '会话标题',
  message_count INT DEFAULT 0 COMMENT '消息条数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活动时间，会话列表按此倒序',
  KEY idx_user_update (user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表：用户的一次多轮对话';

CREATE TABLE IF NOT EXISTS t_chat_message(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID，主键',
  session_id INT NOT NULL COMMENT '所属会话ID，关联 t_chat_session.id',
  user_id INT NOT NULL COMMENT '所属用户ID，关联 t_user.id',
  role VARCHAR(20) NOT NULL COMMENT '消息角色：user / assistant',
  content MEDIUMTEXT COMMENT '消息正文',
  sources TEXT COMMENT '参考来源，JSON 文本',
  retrieve_query VARCHAR(500) DEFAULT '' COMMENT '改写后的检索 query',
  hit_count INT DEFAULT 0 COMMENT '命中切片数',
  duration_ms INT DEFAULT 0 COMMENT '生成耗时，毫秒',
  token_count INT DEFAULT 0 COMMENT '消耗 token 数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间，用于按日统计与历史清理',
  KEY idx_session (session_id),
  KEY idx_user (user_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表：会话内的提问与回答，含检索与生成统计';

CREATE TABLE IF NOT EXISTS t_notice(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '公告ID，主键',
  title VARCHAR(255) NOT NULL COMMENT '公告标题',
  content MEDIUMTEXT COMMENT '公告正文',
  top INT DEFAULT 0 COMMENT '是否置顶：1 是，0 否',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  KEY idx_top_create (top DESC, create_time ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表：系统公告，置顶优先展示';

CREATE TABLE IF NOT EXISTS t_sys_config(
  config_key VARCHAR(50) PRIMARY KEY COMMENT '配置键，主键',
  config_value VARCHAR(500) COMMENT '配置值',
  remark VARCHAR(255) DEFAULT '' COMMENT '配置说明'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表：RAG 参数与系统提示词，键值对';

CREATE TABLE IF NOT EXISTS t_sys_log(
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID，主键',
  username VARCHAR(50) COMMENT '操作者标识，当前存 userId 或「游客」',
  role VARCHAR(20) COMMENT '操作者角色',
  action VARCHAR(100) COMMENT '操作类型，当前固定为 chat',
  detail VARCHAR(500) COMMENT '操作详情，当前为提问原文前 200 字',
  ip VARCHAR(50) COMMENT '来源 IP',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间，用于审计查询与定期归档',
  KEY idx_log_user_action_time (username, action, create_time),
  KEY idx_log_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答日志表：记录每次提问的操作审计（当前只写入、无读取接口）';

INSERT IGNORE INTO t_sys_config(config_key, config_value, remark) VALUES
('chunk_size','500','切片大小'),
('chunk_overlap','80','切片重叠'),
('rag_top_k_recall','20','混合召回条数'),
('rag_top_n_final','5','最终保留条数'),
('rag_score_threshold','0.3','相似度阈值'),
('rerank_enabled','true','是否启用重排'),
('hybrid_enabled','true','是否启用混合检索'),
('query_rewrite_enabled','true','是否启用query改写'),
('temperature','0.3','LLM温度'),
('history_limit','6','带入历史条数'),
('system_prompt','你是学事通CampusPedia，只依据参考资料回答，资料不足就明说。引用资料时，只在首次引用该来源处标注一次 [来源N]，不要每句话都重复标注。','系统提示');
