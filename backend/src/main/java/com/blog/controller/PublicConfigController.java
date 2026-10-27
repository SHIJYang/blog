package com.blog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.Result;
import com.blog.entity.BlogConfig;
import com.blog.service.BlogConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/configs")
@RequiredArgsConstructor
public class PublicConfigController {

    private final BlogConfigService blogConfigService;

    /**
     * 读者端获取博客的基本展示配置（如博客名称、ICP备案号、关于我）
     */
    @GetMapping("/info")
    public Result<?> getPublicConfigInfo() {
        // 核心改动：仅查询数据库中标识为 is_public = 1 的非删除配置
        List<BlogConfig> configs = blogConfigService.list(new LambdaQueryWrapper<BlogConfig>()
                .eq(BlogConfig::getIsPublic, 1));

        Map<String, String> configMap = new HashMap<>();
        for (BlogConfig config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }
        return Result.success(configMap);
    }
}