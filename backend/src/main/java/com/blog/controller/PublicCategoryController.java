package com.blog.controller;

import com.blog.common.Result;
import com.blog.entity.BlogCategory;
import com.blog.service.BlogCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    private final BlogCategoryService blogCategoryService;

    /**
     * 读者端公开获取所有文章分类（用于前台导航栏或侧边栏分类列表展示）
     */
    @GetMapping("/list")
    public Result<?> getPublicCategories() {
        // 直接从数据库获取所有未逻辑删除的分类
        List<BlogCategory> list = blogCategoryService.list();
        return Result.success(list);
    }
}