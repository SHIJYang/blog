package com.blog.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.entity.BlogComment;
import com.blog.service.BlogCommentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/comments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // 严格限制仅限管理员能够处理后台评论
public class AdminCommentController {

    private final BlogCommentService blogCommentService;

    /**
     * 管理后台分页获取评论列表
     */
    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        Page<BlogComment> page = new Page<>(current, size);
        LambdaQueryWrapper<BlogComment> wrapper = new LambdaQueryWrapper<>();

        if (articleId != null) {
            wrapper.eq(BlogComment::getArticleId, articleId);
        }
        if (status != null) {
            wrapper.eq(BlogComment::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(BlogComment::getNickname, keyword)
                    .or()
                    .like(BlogComment::getContent, keyword));
        }

        wrapper.orderByDesc(BlogComment::getCreateTime);
        blogCommentService.page(page, wrapper);
        return Result.success(page);
    }

    /**
     * 后台审核变更评论状态（0-待审核, 1-审核通过, 2-垃圾评论/封禁）
     */
    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        BlogComment comment = blogCommentService.getById(id);
        if (comment != null) {
            comment.setStatus(status);
            blogCommentService.updateById(comment);
        }
        return Result.success("评论状态成功更新");
    }

    /**
     * 逻辑删除评论
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        blogCommentService.removeById(id);
        return Result.success("评论成功删除");
    }
}