package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.KbCategory;
import com.campuspedia.mapper.KbCategoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb/categories")
public class CategoryController {

    private final KbCategoryMapper categoryMapper;

    public CategoryController(KbCategoryMapper categoryMapper) { this.categoryMapper = categoryMapper; }

    @GetMapping
    public Result<List<KbCategory>> list() {
        return Result.ok(categoryMapper.selectList(new QueryWrapper<KbCategory>().orderByAsc("sort_num")));
    }

    @PostMapping
    public Result<Void> create(@RequestBody KbCategory category) { categoryMapper.insert(category); return Result.ok(null); }
}
