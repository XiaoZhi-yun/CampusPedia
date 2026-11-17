package com.campuspedia.service;

import com.campuspedia.mapper.DocChunkMapper;
import com.campuspedia.mapper.KbDocumentMapper;
import com.campuspedia.rag.DocumentLoader;
import com.campuspedia.rag.PgVectorStore;
import com.campuspedia.rag.RecursiveSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 上传安全回归：文件名净化（防路径穿越）与落盘目录边界。
 * 依赖全部 mock，文件写入落在 JUnit @TempDir，不触碰项目目录。
 */
class KnowledgeServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(
                mock(KbDocumentMapper.class),
                mock(DocChunkMapper.class),
                mock(DocumentLoader.class),
                mock(RecursiveSplitter.class),
                mock(Chunker.class),
                mock(EmbeddingService.class),
                mock(PgVectorStore.class),
                mock(ConfigService.class));
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());
    }

    private String sanitize(String raw) {
        return ReflectionTestUtils.invokeMethod(service, "sanitizeFileName", (Object) raw);
    }

    @Test
    void stripsUnixPathTraversal() {
        assertEquals("application.yml", sanitize("../../application.yml"));
    }

    @Test
    void stripsWindowsPathTraversal() {
        assertEquals("pom.xml", sanitize("..\\..\\pom.xml"));
    }

    @Test
    void stripsDirectoryPrefix() {
        assertEquals("制度.md", sanitize("corpus/2024/制度.md"));
    }

    @Test
    void rejectsDotSegments() {
        assertEquals("upload.bin", sanitize(".."));
        assertEquals("upload.bin", sanitize("."));
    }

    @Test
    void rejectsNullAndBlankName() {
        assertEquals("upload.bin", sanitize(null));
        assertEquals("upload.bin", sanitize(""));
    }

    @Test
    void keepsChineseAndReplacesIllegalChars() {
        String safe = sanitize("学籍 管理@#.md");
        assertTrue(safe.contains("学籍"));
        assertFalse(safe.contains("/") || safe.contains("\\") || safe.contains(" "));
        assertTrue(safe.endsWith(".md"));
    }

    @Test
    void truncatesVeryLongName() {
        String safe = sanitize("a".repeat(200) + ".txt");
        assertTrue(safe.length() <= 120);
        assertTrue(safe.endsWith(".txt"));
    }

    @Test
    void savedFileStaysInsideUploadDir() throws Exception {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String path = ReflectionTestUtils.invokeMethod(service, "saveFile", "application.yml", content);

        Path saved = Paths.get(path).toAbsolutePath().normalize();
        Path expectedDir = tempDir.resolve("original").toAbsolutePath().normalize();
        assertTrue(saved.startsWith(expectedDir), "文件必须落在 uploads/original 内");
        assertTrue(saved.getFileName().toString().endsWith("_application.yml"));
        assertFalse(saved.getFileName().toString().contains(".."));
        assertEquals("hello", Files.readString(saved));
    }

    @Test
    void maliciousNameCannotEscapeUploadDir() throws Exception {
        String safe = sanitize("../../../../campus-pedia-pom.xml");
        String path = ReflectionTestUtils.invokeMethod(service, "saveFile", safe,
                "x".getBytes(StandardCharsets.UTF_8));

        Path saved = Paths.get(path).toAbsolutePath().normalize();
        assertTrue(saved.startsWith(tempDir.resolve("original").toAbsolutePath().normalize()));
        assertFalse(saved.toString().contains(".."));
    }
}
