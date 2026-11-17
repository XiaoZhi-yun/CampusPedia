package com.campuspedia.controller;
/**
 * 控制层：知识库分类的查询与新增，挂载于 /api/kb/categories。
 * <p>读写均受 SecurityConfig 的 ADMIN 规则保护，下游直接使用 KbCategoryMapper。</p>
 */

import com.campuspedia.common.Result;
import com.campuspedia.entity.KbCategory;
import com.campuspedia.mapper.KbCategoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库分类接口：按 sort_num 返回列表并支持新增分类。
 * <p>直接用 Mapper 完成读写，路径受管理员规则保护。</p>
 */
@RestController
@RequestMapping("/api/kb/categories")
public class CategoryController {

    private final KbCategoryMapper categoryMapper;

    /**
     * 注入分类 Mapper。
     *
     * @param categoryMapper 分类表读写
     */
    public CategoryController(KbCategoryMapper categoryMapper) { this.categoryMapper = categoryMapper; }

    /**
     * 按 sort_num 升序列出全部分类，供前端筛选语料时展示。
     *
     * @return 排好序的分类列表
     */
    @GetMapping
    public Result<List<KbCategory>> list() {
        return Result.ok(categoryMapper.selectList(new QueryWrapper<KbCategory>().orderByAsc("sort_num")));
    }

    /**
     * 新增一个知识库分类，副作用是插入一条分类记录。
     *
     * @param category 待新增的分类实体
     * @return 无 data 的成功响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody KbCategory category) { categoryMapper.insert(category); return Result.ok(null); }
}
