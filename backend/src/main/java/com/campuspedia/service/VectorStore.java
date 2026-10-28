package com.campuspedia.service;

import com.campuspedia.domain.Chunk;

import java.util.List;

/**
 * 向量库抽象：存储切片并提供全量查询。
 *
 * <p>内存实现（{@code MemoryVectorStore}）保证无 Docker 环境也能跑通；
 * pgvector 实现后续接入，通过 Spring 依赖注入切换。</p>
 */
public interface VectorStore {

    /** 写入一个切片。 */
    void add(Chunk chunk);

    /** 返回当前全部切片。 */
    List<Chunk> getAll();

    /** 按文档 id 删除其全部切片（用于文档删除/更新）。 */
    void removeByDocument(String documentId);

    /** 返回切片总数。 */
    int count();
}
