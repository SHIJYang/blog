package com.blog.controller.admin;

import com.blog.common.Result;
import com.blog.entity.BlogConfig;
import com.blog.service.BlogConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Agnes Video V2.0 视频生成代理接口
 * <p>
 * 前端不直接访问 Agnes API（避免暴露 API Key 且绕开 CORS），
 * 统一由后端转发：/api/admin/agnes/** → https://apihub.agnes-ai.com/**
 * <p>
 * API Key 存储在系统配置表 blog_config 中（key = agnes_api_key），
 * 可通过 /api/admin/configs 接口随时修改，无需重启。
 */
@RestController
@RequestMapping("/api/admin/agnes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // 仅限管理员使用
public class AgnesVideoController {

    /** 系统配置表中 Agnes API Key 的配置键名 */
    public static final String AGNES_API_KEY_CONFIG = "agnes_api_key";

    @Value("${agnes.api.base-url:https://apihub.agnes-ai.com}")
    private String baseUrl;

    private final RestClient restClient;
    private final BlogConfigService blogConfigService;

    /**
     * 从系统配置表读取 Agnes API Key（实时读库，改配置即时生效）
     */
    private String getApiKey() {
        return blogConfigService.getConfigValue(AGNES_API_KEY_CONFIG);
    }

    /**
     * 保存 Agnes API Key 到系统配置表（幂等：存在则更新，不存在则新建）
     * POST /api/admin/agnes/api-key
     */
    @PostMapping("/api-key")
    public Result<?> saveApiKey(@RequestBody Map<String, String> body) {
        String apiKey = body.getOrDefault("apiKey", "").trim();
        if (apiKey.isEmpty()) {
            return Result.error(400, "API Key 不能为空");
        }
        BlogConfig existing = blogConfigService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlogConfig>()
                        .eq(BlogConfig::getConfigKey, AGNES_API_KEY_CONFIG));
        com.blog.dto.ConfigSaveDto dto = new com.blog.dto.ConfigSaveDto();
        dto.setConfigKey(AGNES_API_KEY_CONFIG);
        dto.setConfigValue(apiKey);
        dto.setConfigName("Agnes AI API Key");
        dto.setIsPublic(0); // 私有配置，不对外公开
        if (existing == null) {
            blogConfigService.saveConfig(dto);
        } else {
            blogConfigService.updateConfig(dto);
        }
        return Result.success("Agnes API Key 已保存");
    }

    /**
     * 创建视频生成任务（文生视频 / 图生视频 / 关键帧动画）
     * POST /api/admin/agnes/videos
     */
    @PostMapping("/videos")
    public Result<?> createVideo(@RequestBody Map<String, Object> payload) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Result.error(500, "Agnes API Key 未配置，请先在系统配置中保存 agnes_api_key");
        }
        try {
            ResponseEntity<Map> resp = restClient.post()
                    .uri(baseUrl + "/v1/videos")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .toEntity(Map.class);
            return Result.success(resp.getBody());
        } catch (Exception e) {
            return Result.error(500, "Agnes API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 查询视频生成结果（推荐方式：video_id）
     * GET /api/admin/agnes/videos?videoId=xxx
     */
    @GetMapping("/videos")
    public Result<?> getVideo(@RequestParam("videoId") String videoId) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Result.error(500, "Agnes API Key 未配置，请先在系统配置中保存 agnes_api_key");
        }
        try {
            ResponseEntity<Map> resp = restClient.get()
                    .uri(baseUrl + "/agnesapi?video_id=" + videoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toEntity(Map.class);
            return Result.success(resp.getBody());
        } catch (Exception e) {
            return Result.error(500, "Agnes API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查：探测 Agnes API Key 是否已配置
     * GET /api/admin/agnes/health
     */
    @GetMapping("/health")
    public Result<?> health() {
        String apiKey = getApiKey();
        boolean keyConfigured = apiKey != null && !apiKey.isBlank();
        return Result.success(Map.of(
                "keyConfigured", keyConfigured,
                "baseUrl", baseUrl
        ));
    }
}
