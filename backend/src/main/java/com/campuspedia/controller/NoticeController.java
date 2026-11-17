package com.campuspedia.controller;
/**
 * 控制层：公告的增删改查，挂载于 /api/notice。
 * <p>查询公开、写操作由 SecurityConfig 限定仅管理员可访问，下游直接使用 NoticeMapper。</p>
 */

import com.campuspedia.common.Result;
import com.campuspedia.entity.Notice;
import com.campuspedia.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公告接口：列表按置顶与时间排序，并提供新增、修改、删除。
 * <p>查询公开、写操作受管理员规则保护，数据经 NoticeMapper 存取。</p>
 */
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    private final NoticeMapper noticeMapper;

    /**
     * 注入公告 Mapper。
     *
     * @param noticeMapper 公告表读写
     */
    public NoticeController(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /**
     * 按置顶优先、创建时间升序返回公告列表，保证置顶项稳定靠前。
     *
     * @return 排好序的公告列表
     */
    @GetMapping
    public Result<List<Notice>> list() {
        return Result.ok(noticeMapper.selectList(new QueryWrapper<Notice>().orderByDesc("top").orderByAsc("create_time")));
    }

    /**
     * 新增公告，副作用是插入一条公告记录。
     *
     * @param notice 待新增的公告实体
     * @return 无 data 的成功响应
     */
    @PostMapping
    public Result<Void> create(@RequestBody Notice notice) {
        noticeMapper.insert(notice);
        return Result.ok(null);
    }

    /**
     * 按 id 更新公告，副作用是覆盖一条公告记录。
     *
     * @param notice 含待更新字段的公告实体
     * @return 无 data 的成功响应
     */
    @PutMapping
    public Result<Void> update(@RequestBody Notice notice) { noticeMapper.updateById(notice); return Result.ok(null); }

    /**
     * 按 id 删除公告，副作用是移除一条公告记录。
     *
     * @param id 公告主键
     * @return 无 data 的成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return Result.ok(null);
    }
}
