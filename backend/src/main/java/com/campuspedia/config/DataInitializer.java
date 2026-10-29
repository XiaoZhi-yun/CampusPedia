package com.campuspedia.config;

import com.campuspedia.service.KnowledgeService;
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

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final KnowledgeService knowledgeService;

    @Value("${campus.corpus.path:../corpus/example}")
    private String corpusPath;

    public DataInitializer(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path dir = Paths.get(corpusPath);
            if (!Files.isDirectory(dir)) return;
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
                for (Path p : stream) {
                    String fileName = p.getFileName().toString();
                    knowledgeService.upload(fileName, Files.readAllBytes(p), 0L, null);
                    count++;
                }
            }
            log.info("示例语料加载完成：{} 篇", count);
        } catch (Exception e) {
            log.warn("示例语料加载失败（可能数据库未启动）：{}", e.getMessage());
        }
    }
}