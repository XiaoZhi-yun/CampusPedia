package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.Faq;
import com.campuspedia.mapper.FaqMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faq")
public class FaqController {

    private final FaqMapper faqMapper;

    public FaqController(FaqMapper faqMapper) { this.faqMapper = faqMapper; }

    @GetMapping
    public Result<List<Faq>> list() {
        return Result.ok(faqMapper.selectList(new QueryWrapper<Faq>().orderByAsc("id")));
    }

    @PostMapping
    public Result<Void> create(@RequestBody Faq faq) { faqMapper.insert(faq); return Result.ok(null); }

    @PutMapping
    public Result<Void> update(@RequestBody Faq faq) { faqMapper.updateById(faq); return Result.ok(null); }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { faqMapper.deleteById(id); return Result.ok(null); }
}
