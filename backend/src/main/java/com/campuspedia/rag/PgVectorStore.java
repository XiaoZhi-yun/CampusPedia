package com.campuspedia.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PgVectorStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public void add(String id, String content, Map<String, Object> metadata, float[] embedding) {
        String metaJson = toJson(metadata);
        jdbc.update("INSERT INTO vector_store(id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector)",
                id, content, metaJson, vectorString(embedding));
    }

    public List<VectorHit> search(float[] queryEmbedding, int topK) {
        String sql = "SELECT id, content, metadata::text, 1 - (embedding <=> ?::vector) AS sim " +
                "FROM vector_store ORDER BY embedding <=> ?::vector LIMIT ?";
        String vec = vectorString(queryEmbedding);
        return jdbc.query(sql, (rs, i) -> new VectorHit(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("sim")), vec, vec, topK);
    }

    public void removeByDocument(String documentId) {
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", documentId);
    }

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
        try { return objectMapper.writeValueAsString(m); }
        catch (Exception e) { return "{}"; }
    }
}
