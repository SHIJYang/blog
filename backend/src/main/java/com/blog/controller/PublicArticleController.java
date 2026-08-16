package com.blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.entity.BlogArticle;
import com.blog.service.BlogArticleService;
import com.blog.vo.ArticleVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/articles")
@RequiredArgsConstructor
public class PublicArticleController {

    private final BlogArticleService blogArticleService;

    /**
     * 读者端公开文章列表（仅检索已公开发布 status=1 的文章）
     */
    @GetMapping("/page")
    public Result<?> getPublicPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {
        Page<BlogArticle> page = new Page<>(current, size);
        Page<ArticleVo> voPage = blogArticleService.getArticleVoPage(page, 1, categoryId, tagId, keyword);
        return Result.success(voPage);
    }

    /**
     * 读者端公开文章详情（会触发浏览量 views +1）
     */
    @GetMapping("/{id}")
    public Result<?> getPublicDetail(@PathVariable Long id) {
        ArticleVo vo = blogArticleService.getArticleVo(id, true);
        if (vo.getStatus() != 1) {
            return Result.error(403, "无权查看该文章");
        }
        return Result.success(vo);
    }
}