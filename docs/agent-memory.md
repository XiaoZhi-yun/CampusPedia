## [2026-11-03 18:00] 修复向量化失败 & SSE异常处理兼容性问题

- 用户需求：修复上传文档向量化时content列过长截断报错，以及SSE流接口异常时GlobalExceptionHandler返回类型不兼容text/event-stream
- 改动文件：sql/init.sql, backend/sql/migrate_v1.sql, backend/src/main/java/com/campuspedia/rag/RecursiveSplitter.java, backend/src/main/java/com/campuspedia/common/GlobalExceptionHandler.java
- 关键决定：1) init.sql中t_doc_chunk/t_chat_message/t_notice三表的content列由TEXT改为MEDIUMTEXT；2) RecursiveSplitter增加递归逻辑，单个片段超chunkSize时用下一级分隔符继续切分；3) GlobalExceptionHandler增加SSE检测，SSE场景直接写入data:格式事件而非返回Result对象
- 待办/注意：已存在的数据库需执行ALTER TABLE t_doc_chunk MODIFY content MEDIUMTEXT; 被截断的文档需重新向量化