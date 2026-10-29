package com.campuspedia.service;

import com.campuspedia.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void userPromptFormatsSources() {
        PromptBuilder pb = new PromptBuilder();
        RetrievalResult r = new RetrievalResult(
                new Chunk("c1", "d1", "《河南理工大学学生学籍管理规定（修订）》", "学籍", "第十二条",
                        "学生可以申请转专业。", new float[]{1}),
                0.95);
        String prompt = pb.userPrompt("转专业要什么条件", List.of(r));
        assertTrue(prompt.contains("[来源1]"));
        assertTrue(prompt.contains("《河南理工大学学生学籍管理规定（修订）》"));
        assertTrue(prompt.contains("第十二条"));
        assertTrue(prompt.contains("转专业要什么条件"));
    }
}
