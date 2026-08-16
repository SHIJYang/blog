package com.blog.controller;

import com.blog.common.Result;
import com.blog.entity.BlogTag;
import com.blog.service.BlogTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/tags")
@RequiredArgsConstructor
public class PublicTagController {

    private final BlogTagService blogTagService;

    /**
     * 读者端公开获取所有文章标签（用于前台侧边栏“标签云”展示）
     */
    @GetMapping("/list")
    public Result<?> getPublicTags() {
        // 直接从数据库获取所有未逻辑删除的标签
        List<BlogTag> list = blogTagService.list();
        return Result.success(list);
    }
}