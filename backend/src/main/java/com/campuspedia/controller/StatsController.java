package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.mapper.FeedbackMapper;
import com.campuspedia.mapper.KbDocumentMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final KbDocumentMapper documentMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final FeedbackMapper feedbackMapper;

    public StatsController(KbDocumentMapper documentMapper, ChatSessionMapper sessionMapper,
                           ChatMessageMapper messageMapper, FeedbackMapper feedbackMapper) {
        this.documentMapper = documentMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.feedbackMapper = feedbackMapper;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documents", documentMapper.selectCount(null));
        m.put("sessions", sessionMapper.selectCount(null));
        m.put("messages", messageMapper.selectCount(null));
        m.put("feedback", feedbackMapper.selectCount(null));
        return Result.ok(m);
    }

    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> daily() {
        return Result.ok(messageMapper.countByDay());
    }
}
