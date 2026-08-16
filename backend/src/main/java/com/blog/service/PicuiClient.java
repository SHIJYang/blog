package com.blog.service;

import com.blog.common.BusinessException;
import com.blog.entity.BlogConfig;
import com.blog.service.BlogConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * PicUI 图床客户端
 * <p>
 * 上传图片到 https://picui.cn，返回公网可访问 URL 与缩略图 URL。
 * API Token 从系统配置表 blog_config(key=picui_token) 实时读取，管理后台可随时修改。
 * <p>
 * 流程（实测可用）：直接用长期 API Token（Bearer）multipart 上传
 * body: file + permission(0/1) + album_id(=68)
 * 返回: data.links.url（原图） / data.links.thumbnail_url（缩略图）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PicuiClient {

    public static final String PICUI_TOKEN_CONFIG = "picui_token";
    /** 上传到该相册（实测有效） */
    private static final String ALBUM_ID = "68";

    private final BlogConfigService blogConfigService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${picui.api-url:https://picui.cn/api/v1}")
    private String apiUrl;

    /** 上传结果：URL 与缩略图 URL */
    @Data
    public static class UploadResult {
        private String url;
        private String thumbnailUrl;
    }

    /**
     * 从系统配置读取 PicUI Token（实时读取，无需重启）
     */
    private String getToken() {
        BlogConfig config = blogConfigService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlogConfig>()
                        .eq(BlogConfig::getConfigKey, PICUI_TOKEN_CONFIG));
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 上传图片到 PicUI 图床
     *
     * @param fileName  文件名（含扩展名）
     * @param bytes     图片字节
     * @param isPrivate 是否私有（true=仅自己可见）
     * @return 上传结果（原图 URL + 缩略图 URL）
     */
    public UploadResult upload(String fileName, byte[] bytes, boolean isPrivate) {
        String token = getToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(500, "PicUI Token 未配置，请在系统配置中保存 picui_token");
        }
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            form.add("file", fileResource);
            form.add("permission", isPrivate ? "0" : "1");
            form.add("album_id", ALBUM_ID);

            ResponseEntity<String> resp = restClient.post()
                    .uri(apiUrl + "/upload")
                    .headers(h -> {
                        h.setBearerAuth(token);
                        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toEntity(String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            if (!root.path("status").asText("").equalsIgnoreCase("success")
                    && !root.path("status").asBoolean(false)) {
                String msg = root.path("message").asText("上传失败");
                throw new BusinessException(500, "PicUI 上传失败: " + msg);
            }
            JsonNode data = root.path("data");
            JsonNode links = data.path("links");
            String url = links.path("url").asText(null);
            if (url == null || url.isBlank()) {
                // 兼容旧/其它返回：data.url 或 data.public_url
                url = data.path("url").asText(null);
                if (url == null || url.isBlank()) {
                    url = data.path("public_url").asText(null);
                }
            }
            if (url == null || url.isBlank()) {
                throw new BusinessException(500, "PicUI 上传成功但未返回图片地址");
            }
            String thumb = links.path("thumbnail_url").asText(null);

            UploadResult result = new UploadResult();
            result.setUrl(url);
            result.setThumbnailUrl((thumb == null || thumb.isBlank()) ? url : thumb);
            log.info("PicUI 上传成功: {} -> {} (thumb={})", fileName, url, result.getThumbnailUrl());
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PicUI 上传失败: {}", e.getMessage());
            throw new BusinessException(500, "图床服务暂时不可用");
        }
    }
}
