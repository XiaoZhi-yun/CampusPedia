package com.campuspedia.controller;

import com.campuspedia.common.BizException;
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
        if (userId == null) throw new BizException(401, "请先登录后再反馈");
        feedback.setUserId(userId);
        if (feedback.getMessageId() == null) feedback.setMessageId(0L);
        if (feedback.getRating() == null) feedback.setRating(1);
        feedbackMapper.insert(feedback);
        return Result.ok(null);
    }

    @GetMapping
    public Result<List<Feedback>> list() {
        return Result.ok(feedbackMapper.selectList(new QueryWrapper<Feedback>().orderByDesc("id")));
    }
}
