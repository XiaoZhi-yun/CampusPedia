-- 修复 t_doc_chunk.content 列类型：TEXT -> MEDIUMTEXT（避免长文本截断）
ALTER TABLE t_doc_chunk MODIFY content MEDIUMTEXT;