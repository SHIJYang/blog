package com.blog.service.impl;

import com.blog.common.BusinessException;
import com.blog.entity.MemoryPhoto;
import com.blog.mapper.MemoryPhotoMapper;
import com.blog.util.ImageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 记忆照片受控存储（D5）。
 * <p>
 * 新写入的照片一律落本地受控目录，数据库只存相对路径；
 * 对外统一走 {@code /api/memory/photo/{id}} 鉴权读取，磁盘路径不出后端。
 * 历史 picui 公网图属于既成事实（storage=2），不做回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryPhotoService {

    private final MemoryPhotoMapper memoryPhotoMapper;

    @Value("${memory.upload-dir:uploads/memory}")
    private String uploadDir;

    public MemoryPhoto save(MultipartFile file, Long userId, Long memoryId, int sort) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传照片");
        }
        String originalName = StringUtils.defaultIfBlank(file.getOriginalFilename(), "photo.jpg");
        if (!ImageUtil.isSupported(originalName)) {
            throw new BusinessException(400, "不支持的图片格式，支持 JPEG/PNG/WebP/HEIC 等");
        }
        ImageUtil.checkSize((int) file.getSize(), 10);

        try {
            byte[] bytes = ImageUtil.toWebp(file.getBytes());
            String ext = isWebp(bytes) ? "webp" : "jpg";
            String day = LocalDate.now().toString();
            Path dir = Paths.get(uploadDir).resolve(day).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Files.write(dir.resolve(fileName), bytes);

            MemoryPhoto photo = new MemoryPhoto();
            photo.setUserId(userId);
            photo.setMemoryId(memoryId);
            photo.setUrl(day + "/" + fileName);
            photo.setThumbUrl(day + "/" + fileName);
            photo.setStorage(MemoryPhoto.STORAGE_CONTROLLED);
            photo.setSort(sort);
            photo.setCreateTime(LocalDateTime.now());
            memoryPhotoMapper.insert(photo);
            return photo;
        } catch (IOException e) {
            log.error("记忆照片存储失败: {}", e.getMessage());
            throw new BusinessException(500, "照片保存失败");
        }
    }

    /** 对外访问地址（不暴露磁盘路径） */
    public String accessUrl(MemoryPhoto photo) {
        return photo == null ? null : "/api/memory/photo/" + photo.getId();
    }

    public byte[] readBytes(MemoryPhoto photo) {
        if (photo == null || StringUtils.isBlank(photo.getUrl())) {
            throw new BusinessException(404, "照片不存在");
        }
        if (photo.getStorage() != null && photo.getStorage() == MemoryPhoto.STORAGE_PUBLIC) {
            throw new BusinessException(400, "该照片是历史公网图，无需经此接口读取");
        }
        Path path = Paths.get(uploadDir).resolve(photo.getUrl()).toAbsolutePath().normalize();
        // 防目录穿越：解析后必须仍在 uploadDir 之下
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(403, "非法照片路径");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessException(404, "照片文件不存在");
        }
    }

    /** 供多模态 AI 使用：data URL（模型可直读，无需公网可访问） */
    public String dataUrl(MemoryPhoto photo) {
        byte[] bytes = readBytes(photo);
        String mime = isWebp(bytes) ? "image/webp" : "image/jpeg";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public String mimeType(MemoryPhoto photo) {
        return isWebp(readBytes(photo)) ? "image/webp" : "image/jpeg";
    }

    private boolean isWebp(byte[] bytes) {
        return bytes != null && bytes.length > 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F';
    }
}
