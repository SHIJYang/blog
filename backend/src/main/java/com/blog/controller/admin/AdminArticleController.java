package com.blog.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.dto.ArticleSaveDto;
import com.blog.entity.BlogArticle;
import com.blog.security.utils.SecurityUtils;
import com.blog.service.BlogArticleService;
import com.blog.vo.ArticleVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/articles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
public class AdminArticleController {

    private final BlogArticleService blogArticleService;

    /**
     * 管理端检索文章列表（不限制公开状态）
     */
    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {
        Page<BlogArticle> page = new Page<>(current, size);
        Page<ArticleVo> voPage = blogArticleService.getArticleVoPage(page, status, categoryId, tagId, keyword);
        return Result.success(voPage);
    }

    /**
     * 管理端查询文章详情（不需要增加 views 浏览量计数）
     */
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        ArticleVo vo = blogArticleService.getArticleVo(id, false);
        return Result.success(vo);
    }

    /**
     * 撰写并保存文章
     */
    @PostMapping
    public Result<?> create(@Valid @RequestBody ArticleSaveDto dto) {
        Long currentUserId = SecurityUtils.getUserId();
        blogArticleService.saveArticle(dto, currentUserId);
        return Result.success("文章保存成功");
    }

    /**
     * 更新文章
     */
    @PutMapping
    public Result<?> update(@Valid @RequestBody ArticleSaveDto dto) {
        Long currentUserId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        blogArticleService.updateArticle(dto, currentUserId, role);
        return Result.success("文章更新成功");
    }

    /**
     * 删除文章 (触发逻辑删除)
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        blogArticleService.removeById(id);
        return Result.success("文章删除成功");
    }
}