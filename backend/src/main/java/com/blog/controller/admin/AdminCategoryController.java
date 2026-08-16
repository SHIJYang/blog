package com.blog.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.entity.BlogCategory;
import com.blog.service.BlogCategoryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
public class AdminCategoryController {

    private final BlogCategoryService blogCategoryService;

    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Page<BlogCategory> page = new Page<>(current, size);
        LambdaQueryWrapper<BlogCategory> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(BlogCategory::getName, keyword);
        }
        wrapper.orderByDesc(BlogCategory::getCreateTime);
        blogCategoryService.page(page, wrapper);
        return Result.success(page);
    }

    @PostMapping
    public Result<?> create(@RequestBody BlogCategory category) {
        blogCategoryService.save(category);
        return Result.success("分类创建成功");
    }

    @PutMapping
    public Result<?> update(@RequestBody BlogCategory category) {
        blogCategoryService.updateById(category);
        return Result.success("分类更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        blogCategoryService.removeById(id);
        return Result.success("分类删除成功");
    }
}