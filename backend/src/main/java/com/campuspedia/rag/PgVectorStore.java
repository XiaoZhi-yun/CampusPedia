package com.campuspedia.rag;
/**
 * pgvector 向量库访问，隶属 RAG 检索链路层（rag 包），封装 PostgreSQL 的 {@code vector_store} 表读写。
 *
 * <p>上游：{@code KnowledgeService}（写入/删除向量）、{@code HybridRetriever}（相似度检索）；
 * 下游：独立的 PostgreSQL + pgvector 实例，通过自建 {@code JdbcTemplate} 连接。
 * 该层只做向量与元数据的存取，切片正文与业务属性仍存于 MySQL。</p>
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 直接以 JDBC 操作 pgvector，不经过 ORM：向量列需 {@code ::vector} 显式转型，元数据用 jsonb 存储。
 *
 * <p>向量以字符串字面量传入，维度必须与建表语句的 {@code vector(1024)} 一致，
 * 否则 PostgreSQL 会在写入时拒绝该行。</p>
 */
@Component
public class PgVectorStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 依据配置自建 PG 数据源与 {@code JdbcTemplate}。
     *
     * @param host PG 主机
     * @param port PG 端口
     * @param db   库名
     * @param user 用户名
     * @param pass 密码
     */
    public PgVectorStore(
            @Value("${campus.pgvector.host}") String host,
            @Value("${campus.pgvector.port}") int port,
            @Value("${campus.pgvector.database}") String db,
            @Value("${campus.pgvector.username}") String user,
            @Value("${campus.pgvector.password}") String pass) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
        ds.setUsername(user);
        ds.setPassword(pass);
        this.jdbc = new JdbcTemplate(ds);
    }

    /**
     * 写入一条向量及其正文、元数据。
     *
     * @param id        向量主键，与切片表 {@code vectorId} 对应
     * @param content   切片正文
     * @param metadata  元数据（含 documentId 等），序列化为 jsonb
     * @param embedding 归一化向量，维度须与 {@code vector(1024)} 列一致
     * @throws org.springframework.dao.DataAccessException 维度不符或写入失败时由 JDBC 抛出
     */
    public void add(String id, String content, Map<String, Object> metadata, float[] embedding) {
        String metaJson = toJson(metadata);
        // 显式 ::jsonb / ::vector 转型，让 PG 按列类型解析字符串参数
        jdbc.update("INSERT INTO vector_store(id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector)",
                id, content, metaJson, vectorString(embedding));
    }

    /**
     * 按余弦距离做近邻检索。
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回条数上限
     * @return 按相似度降序的命中列表；相似度由 SQL 计算为 1 - 余弦距离
     */
    public List<VectorHit> search(float[] queryEmbedding, int topK) {
        // <=> 为余弦距离，1 - 距离换算成相似度；同一 vec 需绑定两处占位符
        String sql = "SELECT id, content, metadata::text, 1 - (embedding <=> ?::vector) AS sim " +
                "FROM vector_store ORDER BY embedding <=> ?::vector LIMIT ?";
        String vec = vectorString(queryEmbedding);
        return jdbc.query(sql, (rs, i) -> new VectorHit(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("sim")), vec, vec, topK);
    }

    /**
     * 按 metadata 中的 documentId 删除该文档的全部向量。
     *
     * @param documentId 文档 id，以字符串形式匹配 jsonb 字段
     */
    public void removeByDocument(String documentId) {
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", documentId);
    }

    /**
     * 统计向量总数。
     *
     * @return 记录条数；查询为空时返回 0
     */
    public int count() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
        return c == null ? 0 : c;
    }

    private String vectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }

    private String toJson(Map<String, Object> m) {
        // 元数据序列化失败时退化为空 jsonb {}，不让脏元数据阻断向量写入
        try { return objectMapper.writeValueAsString(m); }
        catch (Exception e) { return "{}"; }
    }
}
