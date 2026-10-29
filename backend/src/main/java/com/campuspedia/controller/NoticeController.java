package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.Notice;
import com.campuspedia.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    private final NoticeMapper noticeMapper;

    public NoticeController(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @GetMapping
    public Result<List<Notice>> list() {
        return Result.ok(noticeMapper.selectList(new QueryWrapper<Notice>().orderByDesc("top").orderByDesc("id")));
    }

    @PostMapping
    public Result<Void> create(@RequestBody Notice notice) {
        noticeMapper.insert(notice);
        return Result.ok(null);
    }
}
