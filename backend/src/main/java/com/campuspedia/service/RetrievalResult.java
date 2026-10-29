package com.campuspedia.service;

import com.campuspedia.domain.Chunk;

/** 检索结果：切片 + 与问题的相似度。 */
public record RetrievalResult(Chunk chunk, double similarity) {}
