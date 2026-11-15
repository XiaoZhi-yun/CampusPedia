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

    @PutMapping
    public Result<Void> update(@RequestBody Notice notice) { noticeMapper.updateById(notice); return Result.ok(null); }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        Long maxId = noticeMapper.selectMaxId();
        noticeMapper.resetAutoIncrement((maxId == null ? 0 : maxId) + 1);
        return Result.ok(null);
    }
}
