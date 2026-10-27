package com.blog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.Result;
import com.blog.entity.BlogLink;
import com.blog.service.BlogLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/links")
@RequiredArgsConstructor
public class PublicLinkController {

    private final BlogLinkService blogLinkService;

    /**
     * 读者端获取公开的友情链接列表 (只筛选 status=1 且按 sort 升序)
     */
    @GetMapping("/list")
    public Result<?> getPublicLinks() {
        List<BlogLink> list = blogLinkService.list(new LambdaQueryWrapper<BlogLink>()
                .eq(BlogLink::getStatus, 1) // 仅显示状态为“显示”的友链
                .orderByAsc(BlogLink::getSort));
        return Result.success(list);
    }
}