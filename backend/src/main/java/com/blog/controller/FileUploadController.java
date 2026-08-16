package com.blog.controller;

import com.blog.common.BusinessException;
import com.blog.common.Result;
import com.blog.service.PicuiClient;
import com.blog.util.ImageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 通用图片上传接口
 * <p>
 * 只上传图片到图床，返回公网可访问 URL。供文章封面、头像、富文本插图等使用。
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final PicuiClient picuiClient;

    /**
     * 上传图片，返回公网 URL
     *
     * @param file      图片文件（JPEG/PNG/WebP/GIF/BMP/HEIC/HEIF）
     * @param isPrivate 是否私有（0/1，默认0公开）
     * @return { "url": "https://..." }
     */
    @PostMapping("/image")
    public Result<?> uploadImage(@RequestParam("file") MultipartFile file,
                                 @RequestParam(defaultValue = "0") Integer isPrivate) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请上传图片");
        }
        String originalName = StringUtils.defaultIfBlank(file.getOriginalFilename(), "photo.jpg");
        if (!ImageUtil.isSupported(originalName)) {
            return Result.error(400, "不支持的图片格式，支持 JPEG/PNG/WebP/GIF/BMP/HEIC/HEIF");
        }
        ImageUtil.checkSize((int) file.getSize(), 10);

        try {
            PicuiClient.UploadResult up;
            try {
                up = picuiClient.upload(originalName, file.getBytes(),
                        isPrivate != null && isPrivate == 1);
            } catch (BusinessException e) {
                return Result.error(e.getCode(), e.getMessage());
            }
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("url", up.getUrl());
            result.put("thumbnail_url", up.getThumbnailUrl() != null ? up.getThumbnailUrl() : up.getUrl());
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage());
            return Result.error(500, "图片上传失败");
        }
    }
}
