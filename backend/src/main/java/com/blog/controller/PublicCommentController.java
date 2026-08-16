package com.blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.dto.CommentSaveDto;
import com.blog.service.BlogCommentService;
import com.blog.vo.CommentVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/comments")
@RequiredArgsConstructor
public class PublicCommentController {

    private final BlogCommentService blogCommentService;

    /**
     * 分页查询当前文章的评论详情（高内聚两级平铺）
     */
    @GetMapping("/page")
    public Result<?> getComments(
            @RequestParam Long articleId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<CommentVo> voPage = blogCommentService.getCommentPageByArticleId(articleId, current, size);
        return Result.success(voPage);
    }

    /**
     * 读者发表新评论
     */
    @PostMapping
    public Result<?> postComment(@Valid @RequestBody CommentSaveDto dto, HttpServletRequest request) {
        String ipAddress = getIpAddress(request);
        blogCommentService.saveComment(dto, ipAddress);
        return Result.success("评论成功发表");
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 对于有多层前置代理服务器的情况，截取首个真实客户端 IP
        if (StringUtils.isNotBlank(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}