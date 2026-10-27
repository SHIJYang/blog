package com.blog.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.entity.BlogTag;
import com.blog.service.BlogTagService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
public class AdminTagController {

    private final BlogTagService blogTagService;

    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Page<BlogTag> page = new Page<>(current, size);
        LambdaQueryWrapper<BlogTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(BlogTag::getName, keyword);
        }
        wrapper.orderByDesc(BlogTag::getCreateTime);
        blogTagService.page(page, wrapper);
        return Result.success(page);
    }

    @PostMapping
    public Result<?> create(@RequestBody BlogTag tag) {
        blogTagService.save(tag);
        return Result.success("标签创建成功");
    }

    @PutMapping
    public Result<?> update(@RequestBody BlogTag tag) {
        blogTagService.updateById(tag);
        return Result.success("标签更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        blogTagService.removeById(id);
        return Result.success("标签删除成功");
    }
}