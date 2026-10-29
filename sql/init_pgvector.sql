CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS vector_store(
  id VARCHAR(64) PRIMARY KEY,
  content TEXT,
  metadata JSONB,
  embedding vector(1024)
);
