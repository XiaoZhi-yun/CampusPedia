package com.campuspedia.config;

import com.campuspedia.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动时自动加载示例语料（corpus/example/*.md），便于开箱即用演示与测试。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final DocumentService documentService;

    @Value("${campus-pedia.corpus.path:../corpus/example}")
    private String corpusPath;

    public DataInitializer(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path dir = Paths.get(corpusPath);
        if (!Files.isDirectory(dir)) {
            log.info("示例语料目录不存在，跳过自动加载：{}", dir.toAbsolutePath());
            return;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path p : stream) {
                String fileName = p.getFileName().toString();
                byte[] bytes = Files.readAllBytes(p);
                documentService.ingest(fileName, extractCategory(fileName), null, bytes);
                count++;
            }
        } catch (Exception e) {
            log.warn("示例语料自动加载失败：{}", e.getMessage());
        }
        log.info("示例语料自动加载完成：{} 篇", count);
    }

    private String extractCategory(String fileName) {
        String base = fileName;
        if (base.toLowerCase().endsWith(".md")) { base = base.substring(0, base.length() - 3); }
        int idx = base.indexOf("-");
        return idx > 0 ? base.substring(0, idx) : "未分类";
    }
}