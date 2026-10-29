-- CampusPedia MySQL 初始化（库名 campus_pedia）
CREATE DATABASE IF NOT EXISTS campus_pedia DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_pedia;

CREATE TABLE IF NOT EXISTS t_user(
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50) DEFAULT '',
  role VARCHAR(20) DEFAULT 'user',
  status INT DEFAULT 1,
  avatar VARCHAR(255) DEFAULT '',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_kb_category(
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  remark VARCHAR(255) DEFAULT '',
  sort_num INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_kb_document(
  id INT AUTO_INCREMENT PRIMARY KEY,
  category_id INT NOT NULL DEFAULT 0,
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(20) DEFAULT '',
  file_size INT DEFAULT 0,
  file_path VARCHAR(500) DEFAULT '',
  source_url VARCHAR(500) DEFAULT '',
  vector_status VARCHAR(20) DEFAULT 'pending',
  chunk_count INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FULLTEXT KEY ft_name (file_name) WITH PARSER ngram
);

CREATE TABLE IF NOT EXISTS t_doc_chunk(
  id INT AUTO_INCREMENT PRIMARY KEY,
  document_id INT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT,
  char_count INT DEFAULT 0,
  clause_ref VARCHAR(255) DEFAULT '',
  vector_id VARCHAR(100) NOT NULL,
  KEY idx_doc (document_id),
  FULLTEXT KEY ft_content (content) WITH PARSER ngram
);

CREATE TABLE IF NOT EXISTS t_chat_session(
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  title VARCHAR(100) DEFAULT '新会话',
  message_count INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_chat_message(
  id INT AUTO_INCREMENT PRIMARY KEY,
  session_id INT NOT NULL,
  user_id INT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT,
  sources TEXT,
  retrieve_query VARCHAR(500) DEFAULT '',
  hit_count INT DEFAULT 0,
  duration_ms INT DEFAULT 0,
  token_count INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_session (session_id)
);

CREATE TABLE IF NOT EXISTS t_faq(
  id INT AUTO_INCREMENT PRIMARY KEY,
  question VARCHAR(255) NOT NULL,
  answer TEXT,
  category_id INT DEFAULT 0,
  hit_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_feedback(
  id INT AUTO_INCREMENT PRIMARY KEY,
  message_id INT NOT NULL,
  user_id INT NOT NULL,
  rating INT NOT NULL,
  comment VARCHAR(500) DEFAULT '',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_notice(
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  content TEXT,
  top INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_sys_config(
  config_key VARCHAR(50) PRIMARY KEY,
  config_value VARCHAR(500),
  remark VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS t_sys_log(
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50),
  role VARCHAR(20),
  action VARCHAR(100),
  detail VARCHAR(500),
  ip VARCHAR(50),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO t_sys_config (config_key, config_value, remark) VALUES
('rag_top_k_recall','20','混合召回粗排条数'),
('rag_top_n_final','5','rerank后保留条数'),
('rag_score_threshold','0.3','相似度阈值'),
('rerank_enabled','true','是否启用rerank'),
('hybrid_enabled','true','是否启用混合检索'),
('query_rewrite_enabled','true','是否启用query改写'),
('temperature','0.3','LLM温度'),
('history_limit','6','带入历史条数'),
('chunk_size','500','切片大小'),
('chunk_overlap','80','切片重叠'),
('system_prompt','你是学事通CampusPedia，只依据参考资料回答，资料不足明说，结论后标[1][2]','系统提示')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);
