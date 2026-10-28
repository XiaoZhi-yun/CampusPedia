package com.campuspedia.service;

import com.campuspedia.domain.Chunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存向量库实现：用线程安全的 {@link CopyOnWriteArrayList} 存储切片。
 * 用于无 Docker / 无 pgvector 环境的回退方案，重启后数据清空（演示可接受）。
 */
@Component
public class MemoryVectorStore implements VectorStore {

    private final List<Chunk> chunks = new CopyOnWriteArrayList<>();

    @Override
    public void add(Chunk chunk) {
        chunks.add(chunk);
    }

    @Override
    public List<Chunk> getAll() {
        return List.copyOf(chunks);
    }

    @Override
    public void removeByDocument(String documentId) {
        chunks.removeIf(c -> c.documentId().equals(documentId));
    }

    @Override
    public int count() {
        return chunks.size();
    }
}
