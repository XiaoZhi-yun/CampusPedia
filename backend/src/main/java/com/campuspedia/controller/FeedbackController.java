package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.Feedback;
import com.campuspedia.mapper.FeedbackMapper;
import com.campuspedia.security.AuthHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackMapper feedbackMapper;
    private final AuthHelper authHelper;

    public FeedbackController(FeedbackMapper feedbackMapper, AuthHelper authHelper) {
        this.feedbackMapper = feedbackMapper;
        this.authHelper = authHelper;
    }

    @PostMapping
    public Result<Void> submit(@RequestBody Feedback feedback, HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        feedback.setUserId(userId == null ? 0L : userId);
        feedbackMapper.insert(feedback);
        return Result.ok(null);
    }

    @GetMapping
    public Result<List<Feedback>> list() {
        return Result.ok(feedbackMapper.selectList(new QueryWrapper<Feedback>().orderByDesc("id")));
    }
}
