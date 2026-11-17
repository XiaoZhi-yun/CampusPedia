package com.campuspedia.service;
/** 覆盖模块：知识库上传文件名净化与落盘边界；回归目标：路径穿越、非法字符、超长名与目录逃逸。 */

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
        // 用 TempDir 覆盖 storagePath，落盘全部发生在临时目录，测试不会在项目目录留下文件。
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());
    }

    private String sanitize(String raw) {
        return ReflectionTestUtils.invokeMethod(service, "sanitizeFileName", (Object) raw);
    }

    /**
     * 被测行为：净化 Unix 风格 ../ 路径穿越前缀。
     * 前置：文件名 ../../application.yml。
     * 期望：只保留末段 application.yml。
     * 回归：上传名携带相对路径导致写到上传目录之外的缺陷。
     */
    @Test
    void stripsUnixPathTraversal() {
        assertEquals("application.yml", sanitize("../../application.yml"));
    }

    /**
     * 被测行为：净化 Windows 风格反斜杠路径穿越前缀。
     * 前置：带两级反斜杠穿越、指向 pom.xml 的文件名。
     * 期望：只保留 pom.xml，说明跨平台分隔符都被剥离。
     * 回归：仅处理斜杠未处理反斜杠导致 Windows 路径穿越绕过的缺陷。
     */
    @Test
    void stripsWindowsPathTraversal() {
        assertEquals("pom.xml", sanitize("..\\..\\pom.xml"));
    }

    /**
     * 被测行为：剥离文件名前的目录层级。
     * 前置：文件名 corpus/2024/制度.md。
     * 期望：只保留末段 制度.md。
     * 回归：带目录的文件名导致上传目录被创建出子路径的缺陷。
     */
    @Test
    void stripsDirectoryPrefix() {
        assertEquals("制度.md", sanitize("corpus/2024/制度.md"));
    }

    /**
     * 被测行为：文件名恰为单点或双点段时退化为默认名。
     * 前置：两种纯点段输入。
     * 期望：均返回 upload.bin，不产出指向目录的可写路径。
     * 回归：点段名被当作合法文件名、解析后指向上传目录本身的缺陷。
     */
    @Test
    void rejectsDotSegments() {
        assertEquals("upload.bin", sanitize(".."));
        assertEquals("upload.bin", sanitize("."));
    }

    /**
     * 被测行为：null 与空串文件名退化为默认名。
     * 前置：分别传入 null 与空串。
     * 期望：均返回 upload.bin 且不抛 NPE。
     * 回归：空文件名未兜底导致 NPE 或落盘到目录路径的缺陷。
     */
    @Test
    void rejectsNullAndBlankName() {
        assertEquals("upload.bin", sanitize(null));
        assertEquals("upload.bin", sanitize(""));
    }

    /**
     * 被测行为：保留中文、替换空白与非法符号、保留扩展名。
     * 前置：文件名 学籍 管理@#.md。
     * 期望：含中文、不含路径分隔符与空格、且以 .md 结尾。
     * 回归：净化时误删中文或丢掉扩展名导致文件不可读不可归类的缺陷。
     */
    @Test
    void keepsChineseAndReplacesIllegalChars() {
        String safe = sanitize("学籍 管理@#.md");
        assertTrue(safe.contains("学籍"));
        assertFalse(safe.contains("/") || safe.contains("\\") || safe.contains(" "));
        assertTrue(safe.endsWith(".md"));
    }

    /**
     * 被测行为：超长文件名被截断到长度上限且保留扩展名。
     * 前置：200 个字符 a 拼接后追加 .txt。
     * 期望：结果长度不超过 120 且仍以 .txt 结尾。
     * 回归：超长名触发文件系统长度限制、或截断后丢扩展名的缺陷。
     */
    @Test
    void truncatesVeryLongName() {
        String safe = sanitize("a".repeat(200) + ".txt");
        assertTrue(safe.length() <= 120);
        assertTrue(safe.endsWith(".txt"));
    }

    /**
     * 被测行为：saveFile 落盘路径被约束在上传目录 original 子目录内。
     * 前置：setUp 把 storagePath 指向 TempDir，写入内容 hello。
     * 期望：归一化路径以 tempDir/original 开头、文件名带前缀、无相对片段、内容可回读。
     * 回归：落盘路径拼接错误导致文件写到项目目录或被覆盖的缺陷。
     */
    @Test
    void savedFileStaysInsideUploadDir() throws Exception {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String path = ReflectionTestUtils.invokeMethod(service, "saveFile", "application.yml", content);

        Path saved = Paths.get(path).toAbsolutePath().normalize();
        Path expectedDir = tempDir.resolve("original").toAbsolutePath().normalize();
        // 归一化后校验前缀，确认路径拼接没有借相对片段跑出临时上传目录而污染项目目录。
        assertTrue(saved.startsWith(expectedDir), "文件必须落在 uploads/original 内");
        assertTrue(saved.getFileName().toString().endsWith("_application.yml"));
        assertFalse(saved.getFileName().toString().contains(".."));
        assertEquals("hello", Files.readString(saved));
    }

    /**
     * 被测行为：恶意穿越名经净化后再落盘仍无法逃逸上传目录。
     * 前置：多级穿越名先 sanitize 再 saveFile。
     * 期望：最终路径仍在 tempDir/original 内，且路径中不含相对片段。
     * 回归：净化与落盘之间防线脱节、仅靠一层校验仍可目录逃逸的缺陷。
     */
    @Test
    void maliciousNameCannotEscapeUploadDir() throws Exception {
        String safe = sanitize("../../../../campus-pedia-pom.xml");
        String path = ReflectionTestUtils.invokeMethod(service, "saveFile", safe,
                "x".getBytes(StandardCharsets.UTF_8));

        Path saved = Paths.get(path).toAbsolutePath().normalize();
        assertTrue(saved.startsWith(tempDir.resolve("original").toAbsolutePath().normalize()));
        // 净化后再落盘的端到端校验：两层防线都要生效，单看净化结果不足以证明路径未被拼接逃逸。
        assertFalse(saved.toString().contains(".."));
    }
}
